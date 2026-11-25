package co.edu.univalle.fleetops.document.service.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.EventPublicationException;
import co.edu.univalle.fleetops.document.service.application.port.out.EventPublisher;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository.PendingEvent;

/**
 * Application Service: publicador del outbox (ADR-DOC-05).
 *
 * <ol>
 * <li>Toma un lote de eventos PENDING, del más antiguo al más reciente, bloqueados para esta instancia.</li>
 * <li>Publica cada uno y lo marca PUBLISHED.</li>
 * <li>Si el broker rechaza un evento en particular, lo marca FAILED y sigue con el siguiente.</li>
 * <li>Ante un error transitorio, cuenta el intento, deja el evento en PENDING y detiene el lote: los demás esperan
 * al próximo ciclo, sin perder el orden ni insistir contra un broker caído.</li>
 * </ol>
 *
 * <p>La transacción del lote la aplica un decorador de infraestructura: las marcas se confirman al final del lote.
 */
public final class PublishPendingEventsService implements PublishPendingEventsUseCase {

	private final OutboxRepository outbox;

	private final EventPublisher publisher;

	private final BusinessClock clock;

	private final int batchSize;

	public PublishPendingEventsService(OutboxRepository outbox, EventPublisher publisher, BusinessClock clock,
			int batchSize) {
		this.outbox = Objects.requireNonNull(outbox, "outbox");
		this.publisher = Objects.requireNonNull(publisher, "publisher");
		this.clock = Objects.requireNonNull(clock, "clock");
		if (batchSize < 1) {
			throw new IllegalArgumentException("El tamaño del lote debe ser mayor que cero");
		}
		this.batchSize = batchSize;
	}

	@Override
	public PublicationResult publishPending() {
		List<PendingEvent> events = this.outbox.lockPending(this.batchSize);
		int published = 0;
		List<EventFailure> permanentFailures = new ArrayList<>();
		for (PendingEvent event : events) {
			try {
				this.publisher.publish(event);
				this.outbox.markPublished(event.eventId(), this.clock.now());
				published++;
			}
			catch (EventPublicationException ex) {
				EventFailure failure = new EventFailure(event.eventId(), describe(ex));
				this.outbox.recordFailure(event.eventId(), failure.reason(), !ex.retryable());
				if (ex.retryable()) {
					return new PublicationResult(published, permanentFailures, failure, false);
				}
				permanentFailures.add(failure);
			}
		}
		return new PublicationResult(published, permanentFailures, null, events.size() == this.batchSize);
	}

	private static String describe(EventPublicationException ex) {
		return (ex.getMessage() != null) ? ex.getMessage() : ex.getClass().getSimpleName();
	}

}
