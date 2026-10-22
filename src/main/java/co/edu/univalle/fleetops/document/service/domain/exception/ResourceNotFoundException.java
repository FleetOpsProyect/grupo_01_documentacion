package co.edu.univalle.fleetops.document.service.domain.exception;

/**
 * Recurso inexistente. El adaptador REST la traduce a 404.
 */
public class ResourceNotFoundException extends DomainException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

}
