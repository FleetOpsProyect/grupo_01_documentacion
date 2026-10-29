package co.edu.univalle.fleetops.document.service.domain.exception;

/**
 * Recurso inexistente. El adaptador REST la traduce a 404.
 */
public class ResourceNotFoundException extends DomainException {

	private static final long serialVersionUID = 1L;

	public ResourceNotFoundException(String message) {
		super(message);
	}

}
