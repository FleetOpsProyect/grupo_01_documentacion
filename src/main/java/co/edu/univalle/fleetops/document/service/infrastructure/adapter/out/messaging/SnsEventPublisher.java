package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.messaging;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import co.edu.univalle.fleetops.document.service.application.port.out.EventPublicationException;
import co.edu.univalle.fleetops.document.service.application.port.out.EventPublisher;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository.PendingEvent;

/**
 * Adaptador de {@link EventPublisher} sobre AWS SNS (ADR-DOC-12, ADR-DOC-14): publica el sobre como Message y el
 * tipo en el Message Attribute {@code event_type} (String), que filtran las suscripciones SQS de los consumidores. El
 * tópico es estándar (no FIFO): no se envía clave de partición ni de deduplicación.
 *
 * <p>Clasificación de errores (decisión D-2 del 24-11-2026):
 * <ul>
 * <li>Permanentes ({@code InvalidParameter}, {@code InvalidParameterValue}, {@code ValidationException}, o un
 * mensaje mayor que el límite de SNS): el problema es del evento; pasa a FAILED.</li>
 * <li>Todo lo demás es transitorio, incluidos los errores de configuración que afectan a todos los eventos
 * (permisos, tópico inexistente, KMS): el evento sigue en PENDING y se publica cuando se corrija, en lugar de marcar
 * todo el outbox como FAILED.</li>
 * </ul>
 */
public final class SnsEventPublisher implements EventPublisher {

	/**
	 * Nombre del Message Attribute con el tipo de evento (ADR-DOC-14).
	 */
	static final String EVENT_TYPE_ATTRIBUTE = "event_type";

	/**
	 * Tamaño máximo de un mensaje de SNS (256 KiB), incluidos los Message Attributes.
	 */
	static final int MAX_MESSAGE_BYTES = 262_144;

	static final Set<String> PERMANENT_ERROR_CODES = Set.of("InvalidParameter", "InvalidParameterValue",
			"ValidationException");

	private final SnsClient sns;

	private final String topicArn;

	public SnsEventPublisher(SnsClient sns, String topicArn) {
		this.sns = Objects.requireNonNull(sns, "sns");
		this.topicArn = Objects.requireNonNull(topicArn, "topicArn");
	}

	@Override
	public void publish(PendingEvent event) {
		if (!this.topicArn.endsWith(":" + event.topic())) {
			// Configuración: el ARN no corresponde al tópico del evento. Se reintenta cuando se corrija
			throw new EventPublicationException("No hay ARN configurado para el tópico " + event.topic()
					+ " (MESSAGING_DOCUMENT_TOPIC_ARN = " + this.topicArn + ")", true, null);
		}
		if (sizeOf(event) > MAX_MESSAGE_BYTES) {
			throw new EventPublicationException("El evento supera el tamaño máximo de un mensaje de SNS ("
					+ MAX_MESSAGE_BYTES + " bytes)", false, null);
		}
		try {
			this.sns.publish(PublishRequest.builder()
				.topicArn(this.topicArn)
				.message(event.payload())
				.messageAttributes(Map.of(EVENT_TYPE_ATTRIBUTE,
						MessageAttributeValue.builder().dataType("String").stringValue(event.eventType()).build()))
				.build());
		}
		catch (AwsServiceException ex) {
			String code = (ex.awsErrorDetails() != null) ? ex.awsErrorDetails().errorCode() : null;
			boolean permanent = code != null && PERMANENT_ERROR_CODES.contains(code);
			throw new EventPublicationException("SNS rechazó el evento (" + code + "): " + ex.getMessage(), !permanent,
					ex);
		}
		catch (SdkException ex) {
			throw new EventPublicationException("SNS no está disponible: " + ex.getMessage(), true, ex);
		}
	}

	private static int sizeOf(PendingEvent event) {
		return event.payload().getBytes(StandardCharsets.UTF_8).length + EVENT_TYPE_ATTRIBUTE.length()
				+ event.eventType().getBytes(StandardCharsets.UTF_8).length;
	}

}
