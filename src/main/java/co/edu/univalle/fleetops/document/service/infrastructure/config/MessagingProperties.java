package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de la mensajería AWS SNS + SQS (HU-DOC-10 y publicador del outbox; ADR-DOC-05, ADR-DOC-12,
 * ADR-DOC-14). Solo se leen con {@code fleetops.messaging.enabled=true} (perfiles {@code local} y {@code aws}); se
 * validan al arrancar.
 *
 * @param enabled activa los consumidores SQS
 * @param region región de AWS (AWS_REGION): la región seleccionada del proyecto
 * @param endpoint URL del emulador (MESSAGING_ENDPOINT, solo perfil local); vacío en AWS
 * @param accessKeyId credencial ficticia del emulador (solo perfil local); vacío en AWS, donde se usa la cadena de
 * credenciales por defecto (rol de la instancia EC2)
 * @param secretAccessKey par de {@code accessKeyId}
 * @param consumer ajustes de lectura
 * @param queues nombres de las colas propias (DAS sec. 8.2)
 * @param publisher publicador del outbox en el tópico propio
 */
@ConfigurationProperties(prefix = "fleetops.messaging")
public record MessagingProperties(boolean enabled, String region, String endpoint, String accessKeyId,
		String secretAccessKey, Consumer consumer, Queues queues, Publisher publisher) {

	static final int MAX_WAIT_TIME_SECONDS = 20;

	static final int MAX_MESSAGES = 10;

	public MessagingProperties {
		region = blankToNull(region);
		endpoint = blankToNull(endpoint);
		accessKeyId = blankToNull(accessKeyId);
		secretAccessKey = blankToNull(secretAccessKey);
		if (region == null) {
			throw new IllegalArgumentException("AWS_REGION es obligatoria con la mensajería activa");
		}
		if (endpoint != null) {
			URI uri = URI.create(endpoint);
			if (uri.getScheme() == null || uri.getHost() == null) {
				throw new IllegalArgumentException("MESSAGING_ENDPOINT debe ser una URL absoluta: " + endpoint);
			}
		}
		if ((accessKeyId == null) != (secretAccessKey == null)) {
			throw new IllegalArgumentException(
					"Las credenciales estáticas de mensajería deben definirse juntas (access key y secret key)");
		}
		if (consumer == null) {
			throw new IllegalArgumentException("fleetops.messaging.consumer es obligatorio");
		}
		if (queues == null) {
			throw new IllegalArgumentException("fleetops.messaging.queues es obligatorio");
		}
		if (publisher == null) {
			throw new IllegalArgumentException("fleetops.messaging.publisher es obligatorio");
		}
	}

	/**
	 * Hay credenciales estáticas (emulador local). Sin ellas se usa la cadena de credenciales por defecto de AWS.
	 */
	public boolean hasStaticCredentials() {
		return this.accessKeyId != null;
	}

	public URI endpointUri() {
		return this.endpoint == null ? null : URI.create(this.endpoint);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	/**
	 * @param waitTimeSeconds long polling de ReceiveMessage, de 0 a 20 s (recomendado 20)
	 * @param maxMessages mensajes por lectura, de 1 a 10
	 * @param retryDelay espera antes de reintentar cuando SQS no responde (HU-DOC-10, criterio 7)
	 */
	public record Consumer(int waitTimeSeconds, int maxMessages, Duration retryDelay) {

		public Consumer {
			if (waitTimeSeconds < 0 || waitTimeSeconds > MAX_WAIT_TIME_SECONDS) {
				throw new IllegalArgumentException("MESSAGING_WAIT_TIME_SECONDS debe estar entre 0 y 20");
			}
			if (maxMessages < 1 || maxMessages > MAX_MESSAGES) {
				throw new IllegalArgumentException("MESSAGING_MAX_MESSAGES debe estar entre 1 y 10");
			}
			if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
				throw new IllegalArgumentException("MESSAGING_RETRY_DELAY debe ser mayor que cero");
			}
		}

	}

	/**
	 * @param driverEvents cola suscrita a {@code fleetops_driver_events}
	 * @param vehicleEvents cola suscrita a {@code fleetops_vehicle_events}
	 */
	public record Queues(String driverEvents, String vehicleEvents) {

		public Queues {
			if (driverEvents == null || driverEvents.isBlank() || vehicleEvents == null || vehicleEvents.isBlank()) {
				throw new IllegalArgumentException("Los nombres de las colas de mensajería son obligatorios");
			}
		}

	}

	/**
	 * Publicador del outbox (ADR-DOC-05; decisiones D-2, D-3 y D-8 del 24-11-2026).
	 *
	 * @param enabled activa el relevo del outbox; permite apagarlo sin apagar los consumidores
	 * @param topicArn ARN del tópico fleetops_document_events (MESSAGING_DOCUMENT_TOPIC_ARN), en la región del proyecto
	 * @param batchSize eventos por lote, de 1 a 500 (recomendado 50)
	 * @param pollInterval espera entre ciclos y antes de reintentar un error transitorio (recomendado 2 s)
	 * @param callTimeout tiempo máximo de una publicación en SNS, incluidos los reintentos del SDK
	 */
	public record Publisher(boolean enabled, String topicArn, int batchSize, Duration pollInterval,
			Duration callTimeout) {

		static final int MAX_BATCH_SIZE = 500;

		private static final Pattern TOPIC_ARN = Pattern
			.compile("^arn:aws[a-z-]*:sns:[a-z0-9-]+:\\d{12}:[A-Za-z0-9_-]{1,256}$");

		public Publisher {
			topicArn = blankToNull(topicArn);
			if (topicArn == null || !TOPIC_ARN.matcher(topicArn).matches()) {
				throw new IllegalArgumentException(
						"MESSAGING_DOCUMENT_TOPIC_ARN debe ser el ARN de un tópico SNS: " + topicArn);
			}
			if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
				throw new IllegalArgumentException("fleetops.messaging.publisher.batch-size debe estar entre 1 y 500");
			}
			if (isNotPositive(pollInterval)) {
				throw new IllegalArgumentException(
						"fleetops.messaging.publisher.poll-interval debe ser mayor que cero");
			}
			if (isNotPositive(callTimeout)) {
				throw new IllegalArgumentException("fleetops.messaging.publisher.call-timeout debe ser mayor que cero");
			}
		}

		private static boolean isNotPositive(Duration duration) {
			return duration == null || duration.isNegative() || duration.isZero();
		}

	}

}
