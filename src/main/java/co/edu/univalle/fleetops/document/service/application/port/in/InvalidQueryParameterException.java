package co.edu.univalle.fleetops.document.service.application.port.in;

import java.io.Serial;

import co.edu.univalle.fleetops.document.service.domain.exception.DomainException;

/**
 * Un parámetro de consulta falta, tiene formato inválido o está fuera de rango: 400 (DAS sec. 7, «Códigos»). El
 * mensaje nombra el parámetro del contrato en snake_case.
 */
public class InvalidQueryParameterException extends DomainException {

	@Serial
	private static final long serialVersionUID = 1L;

	public InvalidQueryParameterException(String message) {
		super(message);
	}

}
