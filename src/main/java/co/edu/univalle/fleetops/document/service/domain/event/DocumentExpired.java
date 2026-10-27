package co.edu.univalle.fleetops.document.service.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;

/**
 * Un documento quedó vencido: {@code document_expired_event}. En HU-DOC-01 y HU-DOC-02 se emite cuando el
 * documento se registra ya vencido; Vehículos o Conductores agregan el bloqueo DOCUMENT (DAS sec. 8.1).
 *
 * @param documentId documento vencido
 * @param documentType tipo de documento
 * @param owner propietario
 * @param expirationDate fecha en que venció
 * @param occurredAt instante en que se detectó, en UTC
 */
public record DocumentExpired(UUID documentId, DocumentTypeCode documentType, OwnerRef owner,
		LocalDate expirationDate, Instant occurredAt) implements DomainEvent {

	public DocumentExpired {
		Objects.requireNonNull(documentId, "documentId");
		Objects.requireNonNull(documentType, "documentType");
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(expirationDate, "expirationDate");
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

}
