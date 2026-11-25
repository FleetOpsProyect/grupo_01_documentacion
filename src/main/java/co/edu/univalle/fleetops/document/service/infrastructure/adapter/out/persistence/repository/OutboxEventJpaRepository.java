package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

	/**
	 * Lote de eventos PENDING del más antiguo al más reciente (índice ix_outbox_events_pending). FOR UPDATE SKIP
	 * LOCKED: las filas quedan bloqueadas hasta el fin de la transacción y otra instancia toma las siguientes en lugar
	 * de esperar o publicar las mismas.
	 */
	@Query(value = """
			SELECT * FROM document.outbox_events
			WHERE status = 'PENDING'
			ORDER BY created_at, event_id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED""", nativeQuery = true)
	List<OutboxEventJpaEntity> lockPending(@Param("limit") int limit);

	long countByStatus(String status);

	@Query("SELECT min(e.createdAt) FROM OutboxEventJpaEntity e WHERE e.status = 'PENDING'")
	Instant findOldestPendingCreatedAt();

}
