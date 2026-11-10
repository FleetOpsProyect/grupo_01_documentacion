package co.edu.univalle.fleetops.document.service.application.port.in;

/**
 * Resultado de procesar un evento de réplica. En todos los casos el mensaje se puede borrar de la cola: el
 * evento quedó aplicado o no tiene efecto (HU-DOC-10, criterios 3 y 4).
 */
public enum OwnerReplicaSyncOutcome {

	/**
	 * El evento cambió la réplica.
	 */
	APPLIED,

	/**
	 * El event_id ya estaba en processed_events: no se cambia nada (criterio 3).
	 */
	DUPLICATE,

	/**
	 * occurred_at es anterior o igual al último evento aplicado a esa entidad (criterio 4).
	 */
	STALE,

	/**
	 * Baja de un propietario que no está en la réplica: no hay nada que desactivar. Se registra como procesado
	 * para no reintentarlo.
	 */
	UNKNOWN_OWNER

}
