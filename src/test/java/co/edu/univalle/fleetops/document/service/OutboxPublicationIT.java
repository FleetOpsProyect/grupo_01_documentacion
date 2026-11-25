package co.edu.univalle.fleetops.document.service;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase.PublicationResult;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository.PendingEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Publicador del outbox (ADR-DOC-05) de punta a punta contra PostgreSQL 18 real y el emulador Moto: el evento de
 * {@code outbox_events} llega a una cola suscrita al tópico propio con raw message delivery, con el Message Attribute
 * {@code event_type}, y la fila queda PUBLISHED. El relevo automático está apagado
 * ({@code fleetops.messaging.publisher.enabled=false}) para invocar el caso de uso de forma determinista.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OutboxPublicationIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.24")
		.withCopyFileToContainer(MountableFile.forHostPath("database/document_schema.sql"),
				"/docker-entrypoint-initdb.d/01_document_schema.sql");

	@Container
	static final GenericContainer<?> MOTO = new GenericContainer<>(DockerImageName.parse("motoserver/moto:5.2.5"))
		.withExposedPorts(5000)
		.waitingFor(Wait.forHttp("/moto-api/"));

	private static final Region REGION = Region.US_EAST_2;

	private static final String TOPIC = "fleetops_document_events";

	private static final String TOPIC_ARN = "arn:aws:sns:" + REGION.id() + ":123456789012:" + TOPIC;

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	private static SnsClient sns;

	private static SqsClient sqs;

	private static String subscriberQueueUrl;

	@Autowired
	private PublishPendingEventsUseCase useCase;

	@Autowired
	private OutboxRepository outbox;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@DynamicPropertySource
	static void messaging(DynamicPropertyRegistry registry) {
		registry.add("fleetops.messaging.enabled", () -> "true");
		registry.add("fleetops.messaging.region", REGION::id);
		registry.add("fleetops.messaging.endpoint", OutboxPublicationIT::endpoint);
		registry.add("fleetops.messaging.access-key-id", () -> "test");
		registry.add("fleetops.messaging.secret-access-key", () -> "test");
		registry.add("fleetops.messaging.consumer.wait-time-seconds", () -> "1");
		registry.add("fleetops.messaging.consumer.max-messages", () -> "10");
		registry.add("fleetops.messaging.consumer.retry-delay", () -> "1s");
		registry.add("fleetops.messaging.publisher.enabled", () -> "false");
		registry.add("fleetops.messaging.publisher.topic-arn", () -> TOPIC_ARN);
	}

	private static String endpoint() {
		return "http://" + MOTO.getHost() + ":" + MOTO.getMappedPort(5000);
	}

	@BeforeAll
	static void provision() {
		StaticCredentialsProvider credentials = StaticCredentialsProvider
			.create(AwsBasicCredentials.create("test", "test"));
		URI endpoint = URI.create(endpoint());
		sns = SnsClient.builder().region(REGION).endpointOverride(endpoint).credentialsProvider(credentials).build();
		sqs = SqsClient.builder().region(REGION).endpointOverride(endpoint).credentialsProvider(credentials).build();
		// Colas de los consumidores de HU-DOC-10, para que no reintenten en el log durante la prueba
		sqs.createQueue(b -> b.queueName("fleetops_document_driver_events_queue"));
		sqs.createQueue(b -> b.queueName("fleetops_document_vehicle_events_queue"));
		subscriberQueueUrl = sqs.createQueue(b -> b.queueName("outbox_it_subscriber_queue")).queueUrl();
		subscribe();
	}

	/**
	 * Un suscriptor como los de Notificaciones o Reportes: cola SQS con raw message delivery, sin filtro.
	 */
	private static void subscribe() {
		String topicArn = sns.createTopic(b -> b.name(TOPIC)).topicArn();
		String queueArn = sqs.getQueueAttributes(b -> b.queueUrl(subscriberQueueUrl)
			.attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
		sns.subscribe(b -> b.topicArn(topicArn)
			.protocol("sqs")
			.endpoint(queueArn)
			.attributes(Map.of("RawMessageDelivery", "true")));
	}

	@BeforeEach
	void startFromAnEmptyOutbox() {
		// Lo que dejaron otras pruebas no interfiere: se da por publicado y se vacía la cola del suscriptor
		this.jdbc.update("UPDATE document.outbox_events SET status = 'PUBLISHED', published_at = now() "
				+ "WHERE status <> 'PUBLISHED'");
		// PurgeQueue solo se permite una vez por minuto (también en Moto): se leen y borran los mensajes
		List<Message> leftovers;
		do {
			leftovers = sqs.receiveMessage(b -> b.queueUrl(subscriberQueueUrl).maxNumberOfMessages(10)).messages();
			leftovers.forEach(m -> sqs.deleteMessage(d -> d.queueUrl(subscriberQueueUrl)
				.receiptHandle(m.receiptHandle())));
		}
		while (!leftovers.isEmpty());
	}

	@Test
	void should_publish_a_registered_document_with_its_event_type_and_mark_it_published() throws Exception {
		// Arrange: registro por la API, que guarda document_registered_event en el outbox
		UUID vehicle = newVehicle();
		this.mockMvc.perform(post("/documents").contentType(MediaType.APPLICATION_JSON).content("""
				{ "document_type": "SOAT", "owner_type": "VEHICLE", "owner_id": "%s",
				  "document_number": "SOAT-%s", "issuing_entity": "Aseguradora de prueba",
				  "issue_date": "%s", "expiration_date": "%s" }
				""".formatted(vehicle, UUID.randomUUID().toString().substring(0, 18), TODAY.minusMonths(1),
				TODAY.plusYears(1)))).andExpect(status().isCreated());
		UUID eventId = this.jdbc.queryForObject("SELECT event_id FROM document.outbox_events WHERE status = 'PENDING'",
				UUID.class);

		// Act
		PublicationResult result = this.useCase.publishPending();

		// Assert: el suscriptor recibe el sobre (raw delivery) y el Message Attribute event_type
		assertThat(result.published()).isEqualTo(1);
		Message message = receiveOne();
		assertThat(message.body()).contains("\"event_id\": \"" + eventId + "\"")
			.contains("\"event_type\": \"document_registered_event\"")
			.contains("\"source\": \"document\"")
			.doesNotContain("document_number");
		assertThat(message.messageAttributes().get("event_type").stringValue())
			.isEqualTo("document_registered_event");
		assertThat(this.jdbc.queryForMap("""
				SELECT status, attempts, last_error, published_at IS NOT NULL AS published
				FROM document.outbox_events WHERE event_id = ?""", eventId))
			.containsEntry("status", "PUBLISHED")
			.containsEntry("attempts", 1)
			.containsEntry("last_error", null)
			.containsEntry("published", true);
	}

	@Test
	void should_publish_the_backlog_from_the_oldest_event() {
		// Arrange (D-4): tres eventos acumulados, insertados en desorden
		UUID document = newDocument();
		UUID newest = insertPending(document, Instant.parse("2026-11-03T10:00:00Z"));
		UUID oldest = insertPending(document, Instant.parse("2026-11-01T10:00:00Z"));
		UUID middle = insertPending(document, Instant.parse("2026-11-02T10:00:00Z"));

		// Act
		List<UUID> order = new TransactionTemplate(this.transactionManager)
			.execute(status -> this.outbox.lockPending(10).stream().map(PendingEvent::eventId).toList());
		PublicationResult result = this.useCase.publishPending();

		// Assert
		assertThat(order).containsExactly(oldest, middle, newest);
		assertThat(result.published()).isEqualTo(3);
		assertThat(receiveAll(3)).hasSize(3);
	}

	@Test
	void should_not_hand_the_same_event_to_two_publishers() throws Exception {
		// Arrange
		UUID document = newDocument();
		UUID first = insertPending(document, Instant.parse("2026-11-01T10:00:00Z"));
		UUID second = insertPending(document, Instant.parse("2026-11-02T10:00:00Z"));
		CountDownLatch firstLocked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);

		// Act: una instancia bloquea el evento más antiguo y la otra lee mientras tanto (FOR UPDATE SKIP LOCKED)
		CompletableFuture<List<UUID>> holder = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
			List<UUID> locked = this.outbox.lockPending(1).stream().map(PendingEvent::eventId).toList();
			firstLocked.countDown();
			await(release);
			return locked;
		}));
		assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
		List<UUID> other = transaction
			.execute(status -> this.outbox.lockPending(10).stream().map(PendingEvent::eventId).toList());
		release.countDown();

		// Assert
		assertThat(holder.get(10, TimeUnit.SECONDS)).containsExactly(first);
		assertThat(other).containsExactly(second);
	}

	@Test
	void should_keep_the_event_pending_while_the_topic_is_missing_and_publish_it_later() {
		// Arrange: error de configuración que afecta a todos los eventos (D-2: transitorio, sin pasar a FAILED)
		UUID document = newDocument();
		UUID eventId = insertPending(document, Instant.parse("2026-11-01T10:00:00Z"));
		sns.deleteTopic(b -> b.topicArn(TOPIC_ARN));

		// Act
		PublicationResult whileMissing = this.useCase.publishPending();
		subscribe();
		PublicationResult afterFix = this.useCase.publishPending();

		// Assert
		assertThat(whileMissing.interrupted()).isTrue();
		assertThat(whileMissing.transientFailure().eventId()).isEqualTo(eventId);
		assertThat(afterFix.published()).isEqualTo(1);
		assertThat(this.jdbc.queryForMap("SELECT status, attempts FROM document.outbox_events WHERE event_id = ?",
				eventId))
			.containsEntry("status", "PUBLISHED")
			.containsEntry("attempts", 2);
		assertThat(receiveAll(1)).hasSize(1);
	}

	private static Message receiveOne() {
		return receiveAll(1).getFirst();
	}

	private static List<Message> receiveAll(int expected) {
		List<Message> received = new java.util.ArrayList<>();
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (received.size() < expected && System.nanoTime() < deadline) {
			received.addAll(sqs.receiveMessage(b -> b.queueUrl(subscriberQueueUrl)
				.maxNumberOfMessages(10)
				.waitTimeSeconds(1)
				.messageAttributeNames("All")).messages());
		}
		return received;
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private UUID insertPending(UUID document, Instant createdAt) {
		UUID eventId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO document.outbox_events (event_id, event_type, topic, message_key, aggregate_id, payload,
				                                    created_at)
				VALUES (?, 'document_expired_event', 'fleetops_document_events', ?, ?, ?::jsonb, ?)""", eventId,
				document.toString(), document, "{\"event_id\": \"" + eventId + "\"}", Timestamp.from(createdAt));
		return eventId;
	}

	private UUID newVehicle() {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO document.vehicle_replica (vehicle_id, license_plate, is_active, last_event_at) "
				+ "VALUES (?, ?, true, now())", id, "OB" + id.toString().substring(0, 4));
		return id;
	}

	private UUID newDocument() {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO document.documents (document_id, document_type, owner_type, owner_id, document_number,
				                                issuing_entity, issue_date, expiration_date, status)
				VALUES (?, 'SOAT', 'VEHICLE', ?, ?, 'Aseguradora de prueba', ?, ?, 'VALID')""", id, newVehicle(),
				"OBX-" + id.toString().substring(0, 18), TODAY.minusMonths(1), TODAY.plusYears(1));
		return id;
	}

}
