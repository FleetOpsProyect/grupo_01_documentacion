package co.edu.univalle.fleetops.document.service.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Datos de un documento nuevo, ya convertidos a objetos de valor (HU-DOC-01 y HU-DOC-02). Las reglas que dependen
 * del tipo se validan en {@link Document#register}.
 *
 * @param documentId identificador asignado por la aplicación
 * @param owner propietario del documento
 * @param documentNumber número del documento
 * @param issuingEntity entidad que lo expidió
 * @param validity vigencia
 * @param licenseCategory categoría de la licencia; vacía en los documentos de vehículo
 */
public record DocumentRegistration(UUID documentId, OwnerRef owner, DocumentNumber documentNumber,
		String issuingEntity, ValidityPeriod validity, LicenseCategory licenseCategory) {

	public DocumentRegistration {
		Objects.requireNonNull(documentId, "documentId");
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(documentNumber, "documentNumber");
		Objects.requireNonNull(validity, "validity");
	}

}
