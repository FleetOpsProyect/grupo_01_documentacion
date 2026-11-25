package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.OutboxEventJpaRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.config.DocumentServiceProperties;

/**
 * Adaptador de {@link OutboxRepository} sobre {@code outbox_events} (ADR-DOC-05).
 *
 * <ul>
 * <li>{@link #append}: inserta el evento en PENDING dentro de la transacción en curso y avisa
 * ({@link OutboxEventsAppended}) para que el relevo lo publique en cuanto se confirme. Las marcas de tiempo del
 * sobre salen en la zona de negocio (BUSINESS_TIME_ZONE).</li>
 * <li>{@link #lockPending}, {@link #markPublished} y {@link #recordFailure}: los usa el publicador dentro de la
 * transacción del lote.</li>
 * </ul>
 */
@Component
public class OutboxPersistenceAdapter implements OutboxRepository {

	private final EntityManager entityManager;

	private final OutboxEventJpaRepository repository;

	private final JsonMapper jsonMapper;

	private final ZoneId businessZone;

	private final ApplicationEventPublisher applicationEvents;

	public OutboxPersistenceAdapter(EntityManager entityManager, OutboxEventJpaRepository repository,
			JsonMapper jsonMapper, DocumentServiceProperties properties, ApplicationEventPublisher applicationEvents) {
		this.entityManager = entityManager;
		this.repository = repository;
		this.jsonMapper = jsonMapper;
		this.businessZone = properties.businessTimeZone();
		this.applicationEvents = applicationEvents;
	}

	@Override
	public void append(UUID eventId, DomainEvent event, UUID correlationId) {
		String payload = this.jsonMapper
			.writeValueAsString(OutboxEnvelopeMapper.envelope(eventId, event, correlationId, this.businessZone));
		this.entityManager.persist(new OutboxEventJpaEntity(eventId, OutboxEnvelopeMapper.eventTypeOf(event),
				OutboxEnvelopeMapper.TOPIC, event.owner().ownerId().toString(), event.documentId(), payload,
				correlationId, event.occurredAt()));
		this.applicationEvents.publishEvent(new OutboxEventsAppended());
	}

	@Override
	public List<PendingEvent> lockPending(int limit) {
		return this.repository.lockPending(limit)
			.stream()
			.map(entity -> new PendingEvent(entity.getEventId(), entity.getEventType(), entity.getTopic(),
					entity.getPayload(), entity.getCorrelationId()))
			.toList();
	}

	@Override
	public void markPublished(UUID eventId, Instant publishedAt) {
		find(eventId).markPublished(publishedAt);
	}

	@Override
	public void recordFailure(UUID eventId, String error, boolean permanent) {
		find(eventId).recordFailure(error, permanent);
	}

	private OutboxEventJpaEntity find(UUID eventId) {
		OutboxEventJpaEntity entity = this.entityManager.find(OutboxEventJpaEntity.class, eventId);
		if (entity == null) {
			throw new IllegalStateException("El evento " + eventId + " no está en el outbox");
		}
		return entity;
	}

}
