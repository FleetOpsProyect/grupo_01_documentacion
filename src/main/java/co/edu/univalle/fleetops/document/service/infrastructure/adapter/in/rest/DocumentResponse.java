package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * DTO de respuesta de un documento (DAS sec. 7, hoja «Endpoints»). Los campos vacíos viajan como {@code null}.
 *
 * <p>previous_document_id, cancellation_reason y cancelled_at forman parte del contrato, pero siempre van vacíos
 * hasta implementar la renovación (HU-DOC-05) y la anulación (HU-DOC-04).
 */
public record DocumentResponse(UUID documentId, DocumentTypeCode documentType, OwnerType ownerType, UUID ownerId,
		String documentNumber, String issuingEntity, LocalDate issueDate, LocalDate expirationDate,
		String licenseCategory, DocumentStatus status, Integer daysRemaining, UUID previousDocumentId,
		String cancellationReason, Instant cancelledAt, Instant createdAt, Instant updatedAt) {

}
