package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de la mensajería AWS SQS (HU-DOC-10, ADR-DOC-12, ADR-DOC-14). Solo se leen con
 * {@code fleetops.messaging.enabled=true} (perfiles {@code local} y {@code aws}); se validan al arrancar.
 *
 * @param enabled activa los consumidores SQS
 * @param region región de AWS (AWS_REGION): la región seleccionada del proyecto
 * @param endpoint URL del emulador (MESSAGING_ENDPOINT, solo perfil local); vacío en AWS
 * @param accessKeyId credencial ficticia del emulador (solo perfil local); vacío en AWS, donde se usa la cadena de
 * credenciales por defecto (rol de la instancia EC2)
 * @param secretAccessKey par de {@code accessKeyId}
 * @param consumer ajustes de lectura
 * @param queues nombres de las colas propias (DAS sec. 8.2)
 */
@ConfigurationProperties(prefix = "fleetops.messaging")
public record MessagingProperties(boolean enabled, String region, String endpoint, String accessKeyId,
		String secretAccessKey, Consumer consumer, Queues queues) {

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
	 * @param vehicleEvents cola suscrita a {@code fleetops_vehicle_events} (SUPUESTO)
	 */
	public record Queues(String driverEvents, String vehicleEvents) {

		public Queues {
			if (driverEvents == null || driverEvents.isBlank() || vehicleEvents == null || vehicleEvents.isBlank()) {
				throw new IllegalArgumentException("Los nombres de las colas de mensajería son obligatorios");
			}
		}

	}

}
