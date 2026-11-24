package co.edu.univalle.fleetops.document.service.application.port.in;

/**
 * Puerto de entrada: verificación diaria de vigencia (HU-DOC-06, ADR-DOC-02). La invoca el adaptador programado a
 * la 01:00 hora de Bogotá y al arrancar el servicio, para recuperar una ejecución pendiente.
 */
public interface CheckExpirationsUseCase {

	/**
	 * Ejecuta la verificación de la fecha de negocio actual, una sola vez por fecha entre todas las instancias.
	 *
	 * @return el resumen de la ejecución, o una ejecución no realizada si esa fecha ya se verificó
	 */
	ExpirationCheckResult checkExpirations();

}
