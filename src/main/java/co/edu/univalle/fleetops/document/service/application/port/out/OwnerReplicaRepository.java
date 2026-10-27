package co.edu.univalle.fleetops.document.service.application.port.out;

import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;

/**
 * Puerto de salida: réplicas locales de vehículos y conductores (ADR-DOC-06). Evita llamar por HTTP a esos
 * servicios para validar al propietario.
 */
public interface OwnerReplicaRepository {

	/**
	 * El propietario existe en la réplica y no fue dado de baja (is_active = true, DAS sec. 6.3).
	 */
	boolean isActiveOwner(OwnerRef owner);

}
