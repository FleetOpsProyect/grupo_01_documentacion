package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;

/**
 * Adaptador de {@link OutboxRepository}: inserta el evento en {@code outbox_events} dentro de la transacción en
 * curso (ADR-DOC-05). Con el broker AWS SNS + SQS decidido pero sin publicador en esta versión, las filas quedan en
 * PENDING.
 */
@Component
public class OutboxPersistenceAdapter implements OutboxRepository {

	private final EntityManager entityManager;

	private final JsonMapper jsonMapper;

	public OutboxPersistenceAdapter(EntityManager entityManager, JsonMapper jsonMapper) {
		this.entityManager = entityManager;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void append(UUID eventId, DomainEvent event, UUID correlationId) {
		String payload = this.jsonMapper.writeValueAsString(OutboxEnvelopeMapper.envelope(eventId, event, correlationId));
		this.entityManager.persist(new OutboxEventJpaEntity(eventId, OutboxEnvelopeMapper.eventTypeOf(event),
				OutboxEnvelopeMapper.TOPIC, event.owner().ownerId().toString(), event.documentId(), payload,
				correlationId, event.occurredAt()));
	}

}
