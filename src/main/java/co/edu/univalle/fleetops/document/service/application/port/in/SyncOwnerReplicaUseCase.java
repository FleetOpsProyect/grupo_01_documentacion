package co.edu.univalle.fleetops.document.service.application.port.in;

/**
 * Puerto de entrada: aplicar un evento de Vehículos o Conductores a la réplica local de propietarios (HU-DOC-10,
 * ADR-DOC-06). Lo invoca el consumidor SQS.
 */
public interface SyncOwnerReplicaUseCase {

	/**
	 * Aplica el evento una sola vez (Idempotent Consumer) y descarta los eventos más antiguos que el último
	 * aplicado a esa entidad.
	 *
	 * @return qué pasó con el evento; cualquier excepción significa que no se procesó y debe reintentarse
	 */
	OwnerReplicaSyncOutcome sync(OwnerReplicaEvent event);

}
