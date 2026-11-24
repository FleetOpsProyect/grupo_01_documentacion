package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.application.port.out.IssuedAlertRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.IssuedAlertJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.IssuedAlertJpaRepository;

/**
 * Adaptador de {@link IssuedAlertRepository} sobre {@code issued_alerts}. Inserta en la transacción en curso, junto
 * con el evento del outbox y el estado del documento (HU-DOC-06, criterio 6).
 */
@Component
public class IssuedAlertPersistenceAdapter implements IssuedAlertRepository {

	private final IssuedAlertJpaRepository repository;

	private final EntityManager entityManager;

	private final IdGenerator idGenerator;

	public IssuedAlertPersistenceAdapter(IssuedAlertJpaRepository repository, EntityManager entityManager,
			IdGenerator idGenerator) {
		this.repository = repository;
		this.entityManager = entityManager;
		this.idGenerator = idGenerator;
	}

	@Override
	public Set<Integer> issuedThresholds(UUID documentId) {
		return new HashSet<>(this.repository.findThresholdDaysByDocumentId(documentId));
	}

	@Override
	public void record(UUID documentId, int thresholdDays, UUID outboxEventId, Instant issuedAt) {
		this.entityManager.persist(
				new IssuedAlertJpaEntity(this.idGenerator.newId(), documentId, thresholdDays, outboxEventId, issuedAt));
	}

}
