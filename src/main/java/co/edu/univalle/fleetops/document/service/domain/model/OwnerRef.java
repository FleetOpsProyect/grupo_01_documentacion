package co.edu.univalle.fleetops.document.service.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object: propietario de un documento. Es una referencia lógica a la réplica local de vehículos o
 * conductores, sin llamar por HTTP a esos servicios (ADR-DOC-06).
 *
 * @param ownerType vehículo o conductor
 * @param ownerId identificador del propietario en su servicio de origen
 */
public record OwnerRef(OwnerType ownerType, UUID ownerId) {

	public OwnerRef {
		Objects.requireNonNull(ownerType, "ownerType");
		Objects.requireNonNull(ownerId, "ownerId");
	}

}
