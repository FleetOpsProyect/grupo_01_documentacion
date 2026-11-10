package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.ZoneId;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.config.DocumentServiceProperties;

/**
 * Adaptador de {@link OutboxRepository}: inserta el evento en {@code outbox_events} dentro de la transacción en
 * curso (ADR-DOC-05). Con el broker AWS SNS + SQS decidido pero sin publicador en esta versión, las filas quedan en
 * PENDING. Las marcas de tiempo del sobre salen en la zona de negocio (BUSINESS_TIME_ZONE).
 */
@Component
public class OutboxPersistenceAdapter implements OutboxRepository {

	private final EntityManager entityManager;

	private final JsonMapper jsonMapper;

	private final ZoneId businessZone;

	public OutboxPersistenceAdapter(EntityManager entityManager, JsonMapper jsonMapper,
			DocumentServiceProperties properties) {
		this.entityManager = entityManager;
		this.jsonMapper = jsonMapper;
		this.businessZone = properties.businessTimeZone();
	}

	@Override
	public void append(UUID eventId, DomainEvent event, UUID correlationId) {
		String payload = this.jsonMapper
			.writeValueAsString(OutboxEnvelopeMapper.envelope(eventId, event, correlationId, this.businessZone));
		this.entityManager.persist(new OutboxEventJpaEntity(eventId, OutboxEnvelopeMapper.eventTypeOf(event),
				OutboxEnvelopeMapper.TOPIC, event.owner().ownerId().toString(), event.documentId(), payload,
				correlationId, event.occurredAt()));
	}

}
