package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Command: datos para registrar un documento (HU-DOC-01 y HU-DOC-02), sin tipos de HTTP.
 *
 * @param documentType SOAT, TECHNICAL_INSPECTION, PROPERTY_CARD o DRIVER_LICENSE
 * @param ownerType VEHICLE o DRIVER
 * @param ownerId propietario en la réplica local
 * @param documentNumber número del documento
 * @param issuingEntity entidad que lo expidió
 * @param issueDate fecha de inicio de vigencia
 * @param expirationDate fecha de vencimiento; vacía en PROPERTY_CARD
 * @param licenseCategory categoría de la licencia; solo en DRIVER_LICENSE
 */
public record RegisterDocumentCommand(DocumentTypeCode documentType, OwnerType ownerType, UUID ownerId,
		String documentNumber, String issuingEntity, LocalDate issueDate, LocalDate expirationDate,
		String licenseCategory) {

}
