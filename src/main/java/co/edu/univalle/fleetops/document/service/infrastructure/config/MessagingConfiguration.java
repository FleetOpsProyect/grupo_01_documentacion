package co.edu.univalle.fleetops.document.service.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.SyncOwnerReplicaUseCase;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.EventPublisher;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.application.service.PublishPendingEventsService;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging.OwnerEventMessageMapper;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging.SqsOwnerEventConsumer;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.scheduler.OutboxRelay;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.messaging.SnsEventPublisher;

/**
 * Composition Root de la mensajería (HU-DOC-10 y publicador del outbox; AWS SDK v2 directo). Solo se activa con
 * {@code fleetops.messaging.enabled=true}: sin perfil, el servicio arranca sin AWS y los eventos esperan en el
 * outbox.
 *
 * <p>Perfil {@code local}: endpoint del emulador (Moto) y credenciales ficticias. Perfil {@code aws}: sin endpoint
 * y con la cadena de credenciales por defecto (rol de la instancia EC2); nunca hay claves en el repositorio.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("fleetops.messaging.enabled")
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfiguration {

	@Bean
	SqsClient sqsClient(MessagingProperties properties) {
		SqsClientBuilder builder = SqsClient.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(credentials(properties));
		if (properties.endpointUri() != null) {
			builder.endpointOverride(properties.endpointUri());
		}
		return builder.build();
	}

	/**
	 * Cliente SNS del publicador. El tiempo máximo por publicación (incluidos los reintentos del SDK) acota cuánto
	 * tiempo quedan bloqueadas las filas del lote si SNS no responde.
	 */
	@Bean
	SnsClient snsClient(MessagingProperties properties) {
		SnsClientBuilder builder = SnsClient.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(credentials(properties))
			.overrideConfiguration(ClientOverrideConfiguration.builder()
				.apiCallTimeout(properties.publisher().callTimeout())
				.build());
		if (properties.endpointUri() != null) {
			builder.endpointOverride(properties.endpointUri());
		}
		return builder.build();
	}

	@Bean
	EventPublisher eventPublisher(SnsClient snsClient, MessagingProperties properties) {
		return new SnsEventPublisher(snsClient, properties.publisher().topicArn());
	}

	/**
	 * Publicador del outbox (ADR-DOC-05): un lote por transacción (decorador).
	 */
	@Bean
	PublishPendingEventsUseCase publishPendingEventsUseCase(OutboxRepository outbox, EventPublisher eventPublisher,
			BusinessClock businessClock, MessagingProperties properties,
			PlatformTransactionManager transactionManager) {
		PublishPendingEventsService service = new PublishPendingEventsService(outbox, eventPublisher, businessClock,
				properties.publisher().batchSize());
		return new TransactionalPublishPendingEventsUseCase(service, new TransactionTemplate(transactionManager));
	}

	@Bean
	@ConditionalOnBooleanProperty("fleetops.messaging.publisher.enabled")
	OutboxRelay outboxRelay(PublishPendingEventsUseCase useCase, MessagingProperties properties) {
		return new OutboxRelay(useCase, properties.publisher().pollInterval());
	}

	@Bean
	OwnerEventMessageMapper ownerEventMessageMapper(JsonMapper jsonMapper) {
		return new OwnerEventMessageMapper(jsonMapper);
	}

	@Bean
	SqsOwnerEventConsumer driverEventsConsumer(SqsClient sqsClient, OwnerEventMessageMapper mapper,
			SyncOwnerReplicaUseCase useCase, MessagingProperties properties) {
		return consumer(sqsClient, properties.queues().driverEvents(), mapper, useCase, properties);
	}

	@Bean
	SqsOwnerEventConsumer vehicleEventsConsumer(SqsClient sqsClient, OwnerEventMessageMapper mapper,
			SyncOwnerReplicaUseCase useCase, MessagingProperties properties) {
		return consumer(sqsClient, properties.queues().vehicleEvents(), mapper, useCase, properties);
	}

	private static SqsOwnerEventConsumer consumer(SqsClient sqsClient, String queueName,
			OwnerEventMessageMapper mapper, SyncOwnerReplicaUseCase useCase, MessagingProperties properties) {
		MessagingProperties.Consumer settings = properties.consumer();
		return new SqsOwnerEventConsumer(sqsClient, queueName, mapper, useCase, settings.waitTimeSeconds(),
				settings.maxMessages(), settings.retryDelay());
	}

	private static AwsCredentialsProvider credentials(MessagingProperties properties) {
		if (properties.hasStaticCredentials()) {
			return StaticCredentialsProvider
				.create(AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
		}
		return DefaultCredentialsProvider.builder().build();
	}

}
