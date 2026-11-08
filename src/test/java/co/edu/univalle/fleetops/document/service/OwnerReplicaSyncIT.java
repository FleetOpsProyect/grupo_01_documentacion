package co.edu.univalle.fleetops.document.service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
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
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-DOC-10 de punta a punta: un productor publica en SNS, la suscripción (raw message delivery y FilterPolicy por
 * event_type) entrega en la cola propia, el consumidor actualiza la réplica en PostgreSQL 18 real y el registro de
 * documentos (HU-DOC-01/02) la usa. AWS lo emula Moto ({@code motoserver/moto}), la misma imagen de
 * {@code infra/local-aws}. Si no hay Docker, la clase se omite.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OwnerReplicaSyncIT {

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

	private static final String DRIVER_QUEUE = "fleetops_document_driver_events_queue";

	private static final String DRIVER_DLQ = "fleetops_document_driver_events_dlq";

	private static final String VEHICLE_QUEUE = "fleetops_document_vehicle_events_queue";

	private static final String VEHICLE_DLQ = "fleetops_document_vehicle_events_dlq";

	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	private static SnsClient sns;

	private static SqsClient sqs;

	private static String driverTopic;

	private static String vehicleTopic;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@DynamicPropertySource
	static void messaging(DynamicPropertyRegistry registry) {
		registry.add("fleetops.messaging.enabled", () -> "true");
		registry.add("fleetops.messaging.region", REGION::id);
		registry.add("fleetops.messaging.endpoint", OwnerReplicaSyncIT::endpoint);
		registry.add("fleetops.messaging.access-key-id", () -> "test");
		registry.add("fleetops.messaging.secret-access-key", () -> "test");
		registry.add("fleetops.messaging.consumer.wait-time-seconds", () -> "1");
		registry.add("fleetops.messaging.consumer.max-messages", () -> "10");
		registry.add("fleetops.messaging.consumer.retry-delay", () -> "200ms");
	}

	/**
	 * Lo mismo que hace {@code infra/local-aws/scripts/provision.sh}, con un visibility timeout de 1 s para que un
	 * mensaje inválido llegue pronto a la DLQ.
	 */
	@BeforeAll
	static void provision() {
		StaticCredentialsProvider credentials = StaticCredentialsProvider
			.create(AwsBasicCredentials.create("test", "test"));
		URI endpoint = URI.create(endpoint());
		sns = SnsClient.builder().region(REGION).endpointOverride(endpoint).credentialsProvider(credentials).build();
		sqs = SqsClient.builder().region(REGION).endpointOverride(endpoint).credentialsProvider(credentials).build();
		driverTopic = provisionConsumer("fleetops_driver_events", DRIVER_QUEUE, DRIVER_DLQ,
				"driver_activated_event", "driver_deactivated_event");
		vehicleTopic = provisionConsumer("fleetops_vehicle_events", VEHICLE_QUEUE, VEHICLE_DLQ,
				"vehicle_created_event", "vehicle_decommissioned_event");
	}

	private static String endpoint() {
		return "http://" + MOTO.getHost() + ":" + MOTO.getMappedPort(5000);
	}

	private static String provisionConsumer(String topicName, String queue, String dlq, String... eventTypes) {
		String dlqArn = queueArn(sqs.createQueue(b -> b.queueName(dlq)).queueUrl());
		String queueUrl = sqs.createQueue(b -> b.queueName(queue)
			.attributes(Map.of(QueueAttributeName.VISIBILITY_TIMEOUT, "1", QueueAttributeName.REDRIVE_POLICY,
					"{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"3\"}")))
			.queueUrl();
		String topicArn = sns.createTopic(b -> b.name(topicName)).topicArn();
		String filter = "{\"event_type\":[\"" + String.join("\",\"", eventTypes) + "\"]}";
		sns.subscribe(b -> b.topicArn(topicArn)
			.protocol("sqs")
			.endpoint(queueArn(queueUrl))
			.attributes(Map.of("RawMessageDelivery", "true", "FilterPolicy", filter)));
		return topicArn;
	}

	private static String queueArn(String queueUrl) {
		return sqs.getQueueAttributes(b -> b.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
			.attributes()
			.get(QueueAttributeName.QUEUE_ARN);
	}

	@Test
	void should_create_the_driver_replica_and_accept_its_license() throws Exception {
		// Arrange
		UUID driver = UUID.randomUUID();

		// Act: el evento de ejemplo de Conductores, con un campo desconocido (criterio 5)
		publish(driverTopic, "driver_activated_event", """
				{"event_id": "%s", "event_type": "driver_activated_event", "occurred_at": "2026-11-08T09:00:00-05:00",
				 "source": "driver", "correlation_id": "%s", "unknown_field": true,
				 "data": {"driver_id": "%s", "full_name": "Ana María Restrepo", "license_number": "123"}}
				""".formatted(UUID.randomUUID(), UUID.randomUUID(), driver));

		// Assert
		awaitDriver(driver, true);
		assertThat(this.jdbc.queryForObject("SELECT full_name FROM document.driver_replica WHERE driver_id = ?",
				String.class, driver))
			.isEqualTo("Ana María Restrepo");
		this.mockMvc.perform(post("/documents").contentType(MediaType.APPLICATION_JSON).content(license(driver)))
			.andExpect(status().isCreated());
		// Borrado tras procesarlo (criterio 1)
		await().atMost(TIMEOUT).until(() -> messagesIn(DRIVER_QUEUE) == 0);
	}

	@Test
	void should_apply_a_repeated_event_only_once() {
		// Arrange
		UUID driver = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		publish(driverTopic, "driver_activated_event",
				driverActivated(eventId, driver, "2026-11-08T09:00:00-05:00", "Nombre original"));
		awaitDriver(driver, true);

		// Act: la misma entrega repetida, con otro nombre para notar si se aplica (criterio 3)
		publish(driverTopic, "driver_activated_event",
				driverActivated(eventId, driver, "2026-11-08T09:05:00-05:00", "Nombre repetido"));

		// Assert
		await().atMost(TIMEOUT).until(() -> messagesIn(DRIVER_QUEUE) == 0);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM document.processed_events WHERE event_id = ?",
				Integer.class, eventId))
			.isEqualTo(1);
		assertThat(this.jdbc.queryForObject("SELECT full_name FROM document.driver_replica WHERE driver_id = ?",
				String.class, driver))
			.isEqualTo("Nombre original");
	}

	@Test
	void should_deactivate_the_driver_and_reject_new_documents_with_422() throws Exception {
		// Arrange
		UUID driver = UUID.randomUUID();
		publish(driverTopic, "driver_activated_event",
				driverActivated(UUID.randomUUID(), driver, "2026-11-08T09:00:00-05:00", "Conductor de baja"));
		awaitDriver(driver, true);

		// Act
		publish(driverTopic, "driver_deactivated_event",
				driverDeactivated(UUID.randomUUID(), driver, "2026-11-08T10:00:00-05:00"));

		// Assert
		awaitDriver(driver, false);
		this.mockMvc.perform(post("/documents").contentType(MediaType.APPLICATION_JSON).content(license(driver)))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void should_ignore_an_event_older_than_the_last_applied() {
		// Arrange: alta a las 10:00 en Bogotá
		UUID driver = UUID.randomUUID();
		publish(driverTopic, "driver_activated_event",
				driverActivated(UUID.randomUUID(), driver, "2026-11-08T10:00:00-05:00", "Conductor vigente"));
		awaitDriver(driver, true);
		UUID staleEvent = UUID.randomUUID();

		// Act: baja a las 14:59 UTC (09:59 en Bogotá), anterior al alta (criterio 4)
		publish(driverTopic, "driver_deactivated_event", driverDeactivated(staleEvent, driver, "2026-11-08T14:59:00Z"));

		// Assert
		await().atMost(TIMEOUT)
			.until(() -> this.jdbc.queryForObject("SELECT count(*) FROM document.processed_events WHERE event_id = ?",
					Integer.class, staleEvent) == 1);
		assertThat(this.jdbc.queryForObject("SELECT is_active FROM document.driver_replica WHERE driver_id = ?",
				Boolean.class, driver))
			.isTrue();
	}

	@Test
	void should_create_the_vehicle_replica() {
		// Arrange
		UUID vehicle = UUID.randomUUID();

		// Act
		publish(vehicleTopic, "vehicle_created_event", """
				{"event_id": "%s", "event_type": "vehicle_created_event", "occurred_at": "2026-11-08T09:00:00-05:00",
				 "source": "vehicle", "correlation_id": "%s",
				 "data": {"vehicle_id": "%s", "license_plate": "JKL012"}}
				""".formatted(UUID.randomUUID(), UUID.randomUUID(), vehicle));

		// Assert
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(this.jdbc.queryForList(
				"SELECT license_plate, is_active, last_event_at FROM document.vehicle_replica WHERE vehicle_id = ?",
				vehicle))
			.singleElement()
			.satisfies(row -> {
				assertThat(row).containsEntry("license_plate", "JKL012").containsEntry("is_active", true);
				assertThat(row.get("last_event_at").toString()).isNotBlank();
			}));
	}

	@Test
	void should_send_an_invalid_message_to_the_dlq_after_three_receives() {
		// Arrange: sin event_id ni driver_id (criterio 6)
		String body = """
				{"event_type": "driver_activated_event", "occurred_at": "2026-11-08T09:00:00-05:00",
				 "source": "driver", "data": {"full_name": "Sin identificadores %s"}}""".formatted(UUID.randomUUID());

		// Act
		publish(driverTopic, "driver_activated_event", body);

		// Assert
		String dlqUrl = sqs.getQueueUrl(b -> b.queueName(DRIVER_DLQ)).queueUrl();
		await().atMost(TIMEOUT).untilAsserted(() -> {
			List<Message> messages = sqs.receiveMessage(b -> b.queueUrl(dlqUrl).maxNumberOfMessages(10)).messages();
			assertThat(messages).extracting(Message::body).contains(body);
			messages.forEach(m -> sqs.deleteMessage(d -> d.queueUrl(dlqUrl).receiptHandle(m.receiptHandle())));
		});
	}

	private static void publish(String topicArn, String eventType, String body) {
		sns.publish(b -> b.topicArn(topicArn)
			.message(body)
			.messageAttributes(Map.of("event_type",
					MessageAttributeValue.builder().dataType("String").stringValue(eventType).build())));
	}

	private void awaitDriver(UUID driver, boolean active) {
		await().atMost(TIMEOUT)
			.until(() -> this.jdbc.queryForList("SELECT is_active FROM document.driver_replica WHERE driver_id = ?",
					Boolean.class, driver), rows -> rows.equals(List.of(active)));
	}

	private static int messagesIn(String queue) {
		String url = sqs.getQueueUrl(b -> b.queueName(queue)).queueUrl();
		Map<QueueAttributeName, String> attributes = sqs.getQueueAttributes(b -> b.queueUrl(url)
			.attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
					QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE))
			.attributes();
		return Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
				+ Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
	}

	private static String driverActivated(UUID eventId, UUID driver, String occurredAt, String fullName) {
		return """
				{"event_id": "%s", "event_type": "driver_activated_event", "occurred_at": "%s", "source": "driver",
				 "data": {"driver_id": "%s", "full_name": "%s"}}""".formatted(eventId, occurredAt, driver, fullName);
	}

	private static String driverDeactivated(UUID eventId, UUID driver, String occurredAt) {
		return """
				{"event_id": "%s", "event_type": "driver_deactivated_event", "occurred_at": "%s", "source": "driver",
				 "data": {"driver_id": "%s"}}""".formatted(eventId, occurredAt, driver);
	}

	private static String license(UUID driver) {
		return """
				{ "document_type": "DRIVER_LICENSE", "owner_type": "DRIVER", "owner_id": "%s",
				  "document_number": "LIC-%s", "issuing_entity": "Secretaría de Movilidad de Cali",
				  "issue_date": "%s", "expiration_date": "%s", "license_category": "C1" }
				""".formatted(driver, UUID.randomUUID().toString().substring(0, 18), TODAY.minusYears(1),
				TODAY.plusYears(9));
	}

}
