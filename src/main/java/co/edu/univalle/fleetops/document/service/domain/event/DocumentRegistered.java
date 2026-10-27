package co.edu.univalle.fleetops.document.service.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.LicenseCategory;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.ValidityPeriod;

/**
 * Se registró un documento nuevo: {@code document_registered_event}. Se emite siempre que el registro tiene éxito,
 * sin importar su estado inicial (DAS sec. 8.1). No lleva el número del documento (DAS sec. 4.2).
 *
 * @param documentId documento registrado
 * @param documentType tipo de documento
 * @param owner propietario
 * @param issuingEntity entidad que lo expidió
 * @param validity vigencia
 * @param licenseCategory categoría de la licencia; vacía en los documentos de vehículo
 * @param status estado calculado al registrar
 * @param occurredAt instante del registro, en UTC
 */
public record DocumentRegistered(UUID documentId, DocumentTypeCode documentType, OwnerRef owner,
		String issuingEntity, ValidityPeriod validity, LicenseCategory licenseCategory, DocumentStatus status,
		Instant occurredAt) implements DomainEvent {

	public DocumentRegistered {
		Objects.requireNonNull(documentId, "documentId");
		Objects.requireNonNull(documentType, "documentType");
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(validity, "validity");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

}
