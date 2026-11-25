package co.edu.univalle.fleetops.document.service.application.port.out;

import java.io.Serial;

/**
 * El broker no aceptó un evento. El adaptador decide si el error es transitorio o permanente:
 *
 * <ul>
 * <li>Transitorio ({@code retryable = true}): red, tiempo agotado, limitación de tasa, error del servicio, o una
 * configuración que afecta a todos los eventos (permisos, tópico inexistente). El evento sigue en PENDING y se
 * reintenta en el próximo ciclo, sin espera creciente.</li>
 * <li>Permanente ({@code retryable = false}): el broker rechaza ese evento en particular (por ejemplo, un parámetro
 * inválido). Reintentarlo daría el mismo error: pasa a FAILED.</li>
 * </ul>
 */
public class EventPublicationException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final boolean retryable;

	public EventPublicationException(String message, boolean retryable, Throwable cause) {
		super(message, cause);
		this.retryable = retryable;
	}

	public boolean retryable() {
		return this.retryable;
	}

}
