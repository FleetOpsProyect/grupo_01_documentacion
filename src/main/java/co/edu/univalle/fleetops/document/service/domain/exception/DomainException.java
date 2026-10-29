package co.edu.univalle.fleetops.document.service.domain.exception;

/**
 * Raíz de las excepciones de negocio. No conoce HTTP: el adaptador REST decide el código de respuesta.
 */
public abstract class DomainException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	protected DomainException(String message) {
		super(message);
	}

}
