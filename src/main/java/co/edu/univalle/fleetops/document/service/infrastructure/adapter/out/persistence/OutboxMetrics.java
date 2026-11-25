package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.OutboxEventJpaRepository;

/**
 * Métricas del outbox (DAS sec. 10, «métricas de lag»), registradas en Micrometer:
 *
 * <ul>
 * <li>{@code fleetops.outbox.events{status=pending|failed}}: eventos por publicar y eventos rechazados.</li>
 * <li>{@code fleetops.outbox.oldest.pending.age}: segundos desde que se creó el pendiente más antiguo (0 si no hay):
 * el retraso de la publicación.</li>
 * </ul>
 *
 * <p>Se calculan con una consulta solo cuando alguien las lee (por ejemplo, un exportador de métricas); no forman
 * parte de /health, que debe seguir sano aunque AWS SNS no esté disponible (DAS sec. 10).
 */
@Component
public final class OutboxMetrics {

	static final String EVENTS = "fleetops.outbox.events";

	static final String OLDEST_PENDING_AGE = "fleetops.outbox.oldest.pending.age";

	private final OutboxEventJpaRepository repository;

	private final Clock clock;

	public OutboxMetrics(OutboxEventJpaRepository repository, Clock systemClock, MeterRegistry registry) {
		this.repository = repository;
		this.clock = systemClock;
		Gauge.builder(EVENTS, this, metrics -> metrics.count(OutboxEventJpaEntity.STATUS_PENDING))
			.description("Eventos del outbox por estado")
			.tag("status", "pending")
			.register(registry);
		Gauge.builder(EVENTS, this, metrics -> metrics.count(OutboxEventJpaEntity.STATUS_FAILED))
			.description("Eventos del outbox por estado")
			.tag("status", "failed")
			.register(registry);
		Gauge.builder(OLDEST_PENDING_AGE, this, OutboxMetrics::oldestPendingAgeSeconds)
			.description("Antigüedad del evento pendiente más antiguo")
			.baseUnit("seconds")
			.register(registry);
	}

	double count(String status) {
		return this.repository.countByStatus(status);
	}

	double oldestPendingAgeSeconds() {
		Instant oldest = this.repository.findOldestPendingCreatedAt();
		if (oldest == null) {
			return 0;
		}
		return Math.max(0, Duration.between(oldest, this.clock.instant()).toSeconds());
	}

}
