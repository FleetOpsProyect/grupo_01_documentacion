package co.edu.univalle.fleetops.document.service.domain.model;

/**
 * Estados de un documento (DAS sec. 6.2). REPLACED y CANCELLED son finales.
 */
public enum DocumentStatus {

	VALID,

	EXPIRING_SOON,

	EXPIRED,

	REPLACED,

	CANCELLED;

	/**
	 * Un documento activo cuenta para la regla «un solo documento activo por propietario y tipo» (ADR-DOC-11).
	 */
	public boolean isActive() {
		return this == VALID || this == EXPIRING_SOON || this == EXPIRED;
	}

}
