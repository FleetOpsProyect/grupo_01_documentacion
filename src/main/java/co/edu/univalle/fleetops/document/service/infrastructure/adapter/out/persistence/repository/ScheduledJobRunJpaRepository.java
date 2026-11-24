package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.ScheduledJobRunJpaEntity;

public interface ScheduledJobRunJpaRepository extends JpaRepository<ScheduledJobRunJpaEntity, UUID> {

	Optional<ScheduledJobRunJpaEntity> findByJobNameAndBusinessDate(String jobName, LocalDate businessDate);

	/**
	 * Retoma una ejecución que falló o que quedó en RUNNING más tiempo del permitido (la instancia que la tenía se
	 * cayó). Es atómico: si dos instancias lo intentan a la vez, solo una actualiza la fila.
	 *
	 * @return 1 si esta instancia retomó la ejecución, 0 si no
	 */
	@Modifying
	@Query("""
			UPDATE ScheduledJobRunJpaEntity r
			SET r.status = 'RUNNING', r.instanceId = :instanceId, r.startedAt = :startedAt, r.finishedAt = NULL,
			    r.evaluatedCount = 0, r.issuedCount = 0, r.failedCount = 0
			WHERE r.jobName = :jobName AND r.businessDate = :businessDate
			  AND (r.status = 'FAILED' OR (r.status = 'RUNNING' AND r.startedAt < :staleBefore))""")
	int takeOver(@Param("jobName") String jobName, @Param("businessDate") LocalDate businessDate,
			@Param("instanceId") String instanceId, @Param("startedAt") Instant startedAt,
			@Param("staleBefore") Instant staleBefore);

}
