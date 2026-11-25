package co.edu.univalle.fleetops.document.service.application.port.in;

import co.edu.univalle.fleetops.document.service.domain.exception.DomainException;

/**
 * Un parámetro de la URL es inválido. La API responde 400 con el mensaje de la excepción.
 * Se usa en las consultas con filtros y paginación (HU-DOC-03 y HU-DOC-08).
 */
public class InvalidQueryParameterException extends DomainException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param message mensaje de error que verá el usuario
	 */
	public InvalidQueryParameterException(String message) {
		super(message);
	}

	/**
	 * Error para un filtro con un valor no permitido (HU-DOC-03, criterio 3).
	 *
	 * @param name nombre del filtro como aparece en la URL, por ejemplo document_type
	 */
	public static InvalidQueryParameterException invalidFilter(String name) {
		return new InvalidQueryParameterException("Valor de filtro no válido: " + name);
	}

}
