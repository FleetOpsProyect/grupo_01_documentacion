package co.edu.univalle.fleetops.document.service.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.in.SyncOwnerReplicaUseCase;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging.OwnerEventMessageMapper;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging.SqsOwnerEventConsumer;

/**
 * Composition Root de la mensajería (HU-DOC-10, decisión D-2: AWS SDK v2 directo). Solo se activa con
 * {@code fleetops.messaging.enabled=true}: sin perfil, el servicio arranca sin AWS, como antes.
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
