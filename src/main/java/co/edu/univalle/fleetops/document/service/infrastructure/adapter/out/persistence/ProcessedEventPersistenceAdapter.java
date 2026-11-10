package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import co.edu.univalle.fleetops.document.service.application.port.out.ProcessedEventRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.ProcessedEventJpaEntity;

/**
 * Adaptador de {@link ProcessedEventRepository} sobre {@code processed_events}. Si dos entregas del mismo evento se
 * procesan a la vez, la clave primaria hace fallar a la segunda transacción; su mensaje vuelve a la cola y en el
 * siguiente intento se descarta como repetido.
 */
@Component
public class ProcessedEventPersistenceAdapter implements ProcessedEventRepository {

	private final EntityManager entityManager;

	public ProcessedEventPersistenceAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public boolean exists(UUID eventId) {
		return this.entityManager.find(ProcessedEventJpaEntity.class, eventId) != null;
	}

	@Override
	public void record(UUID eventId, String eventType, String source, Instant processedAt) {
		this.entityManager.persist(new ProcessedEventJpaEntity(eventId, eventType, source, processedAt));
	}

}
