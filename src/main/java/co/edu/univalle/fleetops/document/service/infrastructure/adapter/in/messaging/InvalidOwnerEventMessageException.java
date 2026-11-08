package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging;

import java.io.Serial;

/**
 * El mensaje no es un evento de réplica válido (JSON roto, sin event_id, sin el id de la entidad, event_type no
 * soportado, etc.). El consumidor no lo borra: tras 3 recepciones la redrive policy lo lleva a la DLQ (HU-DOC-10,
 * criterio 6).
 */
public class InvalidOwnerEventMessageException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String eventId;

	public InvalidOwnerEventMessageException(String message, String eventId) {
		super(message);
		this.eventId = eventId;
	}

	public InvalidOwnerEventMessageException(String message, String eventId, Throwable cause) {
		super(message, cause);
		this.eventId = eventId;
	}

	/**
	 * event_id tal como llegó, o {@code null} si el mensaje no lo trae o no se pudo leer.
	 */
	public String eventId() {
		return this.eventId;
	}

}
