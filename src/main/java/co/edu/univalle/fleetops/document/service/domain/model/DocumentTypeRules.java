package co.edu.univalle.fleetops.document.service.domain.model;

import java.util.Objects;

/**
 * Type Object: reglas de un tipo de documento leídas del catálogo {@code document_types} (ADR-DOC-10). El
 * registro, el job y las consultas se rigen por estas reglas y no por condicionales sobre el tipo.
 *
 * @param code tipo de documento
 * @param ownerType único tipo de propietario permitido
 * @param hasExpiration si el tipo vence (false solo en PROPERTY_CARD)
 * @param mandatory si es obligatorio para operar (HU-DOC-09)
 * @param renewable si admite renovación (HU-DOC-05)
 * @param active si el tipo está habilitado en el catálogo
 */
public record DocumentTypeRules(DocumentTypeCode code, OwnerType ownerType, boolean hasExpiration,
		boolean mandatory, boolean renewable, boolean active) {

	public DocumentTypeRules {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(ownerType, "ownerType");
	}

}
