package co.edu.univalle.fleetops.document.service.domain.exception;

/**
 * Conflicto de unicidad o de estado, por ejemplo un segundo documento activo del mismo tipo (ADR-DOC-11).
 * El adaptador REST la traduce a 409.
 */
public class DomainConflictException extends DomainException {

	private static final long serialVersionUID = 1L;

	public DomainConflictException(String message) {
		super(message);
	}

}
