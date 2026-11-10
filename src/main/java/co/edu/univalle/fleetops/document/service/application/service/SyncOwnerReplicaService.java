package co.edu.univalle.fleetops.document.service.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaAction;
import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaEvent;
import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaSyncOutcome;
import co.edu.univalle.fleetops.document.service.application.port.in.SyncOwnerReplicaUseCase;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.OwnerReplicaRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.ProcessedEventRepository;

/**
 * Application Service: mantiene las réplicas de vehículos y conductores a partir de sus eventos (HU-DOC-10).
 *
 * <p>Orden: descartar el event_id repetido (criterio 3), descartar el evento más antiguo que el último aplicado a
 * la entidad (criterio 4), aplicar el alta o la baja y guardar el event_id. La transacción la aplica un decorador
 * de infraestructura: la réplica y processed_events se confirman juntos.
 */
public final class SyncOwnerReplicaService implements SyncOwnerReplicaUseCase {

	private final OwnerReplicaRepository owners;

	private final ProcessedEventRepository processedEvents;

	private final BusinessClock clock;

	public SyncOwnerReplicaService(OwnerReplicaRepository owners, ProcessedEventRepository processedEvents,
			BusinessClock clock) {
		this.owners = Objects.requireNonNull(owners, "owners");
		this.processedEvents = Objects.requireNonNull(processedEvents, "processedEvents");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public OwnerReplicaSyncOutcome sync(OwnerReplicaEvent event) {
		Objects.requireNonNull(event, "event");
		if (this.processedEvents.exists(event.eventId())) {
			return OwnerReplicaSyncOutcome.DUPLICATE;
		}
		Instant now = this.clock.now();
		OwnerReplicaSyncOutcome outcome = apply(event, now);
		this.processedEvents.record(event.eventId(), event.eventType(), event.source(), now);
		return outcome;
	}

	private OwnerReplicaSyncOutcome apply(OwnerReplicaEvent event, Instant now) {
		Optional<Instant> lastEventAt = this.owners.lastEventAt(event.owner());
		if (lastEventAt.isPresent() && !event.occurredAt().isAfter(lastEventAt.get())) {
			return OwnerReplicaSyncOutcome.STALE;
		}
		if (event.action() == OwnerReplicaAction.ACTIVATE) {
			this.owners.activate(event.owner(), event.label(), event.occurredAt(), now);
			return OwnerReplicaSyncOutcome.APPLIED;
		}
		if (lastEventAt.isEmpty()) {
			return OwnerReplicaSyncOutcome.UNKNOWN_OWNER;
		}
		this.owners.deactivate(event.owner(), event.occurredAt(), now);
		return OwnerReplicaSyncOutcome.APPLIED;
	}

}
