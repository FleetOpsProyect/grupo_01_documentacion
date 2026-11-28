package co.edu.univalle.fleetops.document.service.domain.exception;

/**
 * Motivo de anulación ausente o con una longitud inválida (HU-DOC-04).
 */
public class InvalidCancellationReasonException extends BusinessRuleViolationException {

	private static final long serialVersionUID = 1L;

	public InvalidCancellationReasonException() {
		super("El motivo de anulación es obligatorio y debe tener entre 10 y 200 caracteres");
	}

}
