package co.edu.univalle.fleetops.document.service.application.port.in;

/**
 * Efecto de un evento de Vehículos o Conductores sobre la réplica local (HU-DOC-10, criterio 2).
 */
public enum OwnerReplicaAction {

	/**
	 * driver_activated_event o vehicle_created_event: el propietario existe y acepta documentos nuevos.
	 */
	ACTIVATE,

	/**
	 * driver_deactivated_event o vehicle_decommissioned_event: deja de aceptar documentos nuevos; sus documentos
	 * se conservan.
	 */
	DEACTIVATE

}
