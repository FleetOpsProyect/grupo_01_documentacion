package co.edu.univalle.fleetops.document.service.application.port.out;

import java.time.Instant;
import java.util.Optional;

import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;

/**
 * Puerto de salida: réplicas locales de vehículos y conductores (ADR-DOC-06). Evita llamar por HTTP a esos
 * servicios para validar al propietario. Las alimenta el consumidor de eventos (HU-DOC-10).
 */
public interface OwnerReplicaRepository {

	/**
	 * El propietario existe en la réplica y no fue dado de baja (is_active = true, DAS sec. 6.3).
	 */
	boolean isActiveOwner(OwnerRef owner);

	/**
	 * Último occurred_at aplicado a esa entidad (last_event_at), o vacío si no está en la réplica. Bloquea la fila
	 * hasta el fin de la transacción, para que dos eventos de la misma entidad no se apliquen a la vez.
	 */
	Optional<Instant> lastEventAt(OwnerRef owner);

	/**
	 * Crea la entidad o la actualiza con is_active = true.
	 *
	 * @param label placa del vehículo o nombre del conductor
	 */
	void activate(OwnerRef owner, String label, Instant eventAt, Instant updatedAt);

	/**
	 * Marca is_active = false en una entidad que ya existe en la réplica.
	 */
	void deactivate(OwnerRef owner, Instant eventAt, Instant updatedAt);

}
