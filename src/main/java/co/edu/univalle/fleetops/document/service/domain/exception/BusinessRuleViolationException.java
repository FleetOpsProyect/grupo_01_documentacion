package co.edu.univalle.fleetops.document.service.domain.exception;

/**
 * Violación de una regla de negocio (DAS sec. 6.3). El adaptador REST la traduce a 422.
 */
public class BusinessRuleViolationException extends DomainException {

	private static final long serialVersionUID = 1L;

	public BusinessRuleViolationException(String message) {
		super(message);
	}

}
