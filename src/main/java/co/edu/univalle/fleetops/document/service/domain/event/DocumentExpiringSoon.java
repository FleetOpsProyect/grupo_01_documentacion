package co.edu.univalle.fleetops.document.service.domain.event;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;

/**
 * Un documento alcanzó un umbral de alerta: {@code document_expiring_soon_event} (HU-DOC-06, DAS sec. 8.1). Lo
 * publica el job diario de vigencia; Notificaciones avisa al administrador de flota.
 *
 * @param documentId documento por vencer
 * @param documentType tipo de documento (nunca PROPERTY_CARD, que no vence)
 * @param owner propietario
 * @param expirationDate fecha de vencimiento
 * @param thresholdDays umbral de ALERT_THRESHOLD_DAYS que se avisa
 * @param daysRemaining días que faltan para el vencimiento según la fecha de negocio
 * @param occurredAt instante en que se detectó
 */
public record DocumentExpiringSoon(UUID documentId, DocumentTypeCode documentType, OwnerRef owner,
		LocalDate expirationDate, int thresholdDays, int daysRemaining, Instant occurredAt) implements DomainEvent {

	public DocumentExpiringSoon {
		Objects.requireNonNull(documentId, "documentId");
		Objects.requireNonNull(documentType, "documentType");
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(expirationDate, "expirationDate");
		Objects.requireNonNull(occurredAt, "occurredAt");
		if (thresholdDays < 0) {
			throw new IllegalArgumentException("thresholdDays no puede ser negativo");
		}
		if (daysRemaining < 0 || daysRemaining > thresholdDays) {
			throw new IllegalArgumentException("daysRemaining debe estar entre 0 y thresholdDays");
		}
	}

	/**
	 * event_id determinista: el mismo documento y umbral dan siempre el mismo identificador, así que dos instancias
	 * del job o una reejecución no generan eventos distintos (HU-DOC-06, criterio 2). Es un UUID basado en nombre
	 * (RFC 9562, versión 3), no un valor criptográfico.
	 */
	public static UUID eventIdFor(UUID documentId, int thresholdDays) {
		Objects.requireNonNull(documentId, "documentId");
		String name = "document_expiring_soon_event:" + documentId + ":" + thresholdDays;
		return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
	}

}
