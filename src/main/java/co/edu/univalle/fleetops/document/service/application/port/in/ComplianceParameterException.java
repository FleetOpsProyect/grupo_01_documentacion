package co.edu.univalle.fleetops.document.service.application.port.in;

import co.edu.univalle.fleetops.document.service.domain.exception.DomainException;

/**
 * Parámetros inválidos de la consulta de cumplimiento.
 */
public class ComplianceParameterException extends DomainException {

	private static final long serialVersionUID = 1L;

	public ComplianceParameterException() {
		super("Los parámetros owner_type y owner_id son obligatorios y válidos");
	}

}
