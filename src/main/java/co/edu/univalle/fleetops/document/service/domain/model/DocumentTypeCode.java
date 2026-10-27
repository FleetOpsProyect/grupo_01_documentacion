package co.edu.univalle.fleetops.document.service.domain.model;

/**
 * Tipos de documento del servicio (DAS sec. 6.1). Se usan los mismos valores en la API, la base de datos y los
 * eventos.
 */
public enum DocumentTypeCode {

	/** Seguro obligatorio del vehículo. */
	SOAT,

	/** Revisión tecnomecánica del vehículo. */
	TECHNICAL_INSPECTION,

	/** Tarjeta de propiedad del vehículo; no vence. */
	PROPERTY_CARD,

	/** Licencia de conducción del conductor. */
	DRIVER_LICENSE

}
