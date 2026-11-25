package co.edu.univalle.fleetops.document.service.application.port.in;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Puerto de entrada: publica un lote de eventos pendientes del outbox (ADR-DOC-05). Lo invoca el relevo del outbox
 * cada pocos segundos y después de cada commit que guarda eventos.
 */
public interface PublishPendingEventsUseCase {

	/**
	 * Publica, en orden de creación, hasta un lote de eventos PENDING. Entrega «al menos una vez»: si el proceso cae
	 * entre la publicación y la marca, el evento se vuelve a publicar y los consumidores lo descartan por event_id.
	 */
	PublicationResult publishPending();

	/**
	 * Resultado de un lote.
	 *
	 * @param published eventos publicados
	 * @param permanentFailures eventos que el broker rechazó y pasaron a FAILED
	 * @param transientFailure error transitorio que detuvo el lote; vacío si no hubo. El evento sigue en PENDING
	 * @param mayHaveMore el lote se llenó sin errores transitorios: puede haber más pendientes
	 */
	record PublicationResult(int published, List<EventFailure> permanentFailures, EventFailure transientFailure,
			boolean mayHaveMore) {

		public PublicationResult {
			if (published < 0) {
				throw new IllegalArgumentException("published no puede ser negativo");
			}
			permanentFailures = List.copyOf(Objects.requireNonNull(permanentFailures, "permanentFailures"));
		}

		/**
		 * Un error transitorio detuvo el lote: hay que esperar el próximo ciclo.
		 */
		public boolean interrupted() {
			return this.transientFailure != null;
		}

	}

	/**
	 * Evento que no se pudo publicar.
	 *
	 * @param eventId event_id del evento
	 * @param reason motivo, para el log
	 */
	record EventFailure(UUID eventId, String reason) {

		public EventFailure {
			Objects.requireNonNull(eventId, "eventId");
			Objects.requireNonNull(reason, "reason");
		}

	}

}
