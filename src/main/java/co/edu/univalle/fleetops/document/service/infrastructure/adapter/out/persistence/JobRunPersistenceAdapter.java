package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.ScheduledJobRunJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.ScheduledJobRunJpaRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.config.ExpirationJobProperties;
import co.edu.univalle.fleetops.document.service.infrastructure.config.InstanceIdentity;

/**
 * Adaptador de {@link JobRunRepository} sobre {@code scheduled_job_runs} (ADR-DOC-02).
 *
 * <p>Reclamar la fecha es insertar la fila: la restricción única {@code uk_scheduled_job_runs_job_date} deja pasar
 * a una sola instancia. Si la fila ya existe, se retoma solo si la ejecución falló o quedó en RUNNING más de
 * {@code stale-after} (la instancia que la tenía se cayó); así una caída no bloquea la fecha para siempre. Cada
 * operación se confirma en su propia transacción, independiente de la verificación de los documentos.
 */
@Component
public class JobRunPersistenceAdapter implements JobRunRepository {

	static final String UK_JOB_DATE = "uk_scheduled_job_runs_job_date";

	private final ScheduledJobRunJpaRepository repository;

	private final TransactionTemplate requiresNew;

	private final IdGenerator idGenerator;

	private final Duration staleAfter;

	private final String instanceId;

	@Autowired
	public JobRunPersistenceAdapter(ScheduledJobRunJpaRepository repository,
			PlatformTransactionManager transactionManager, IdGenerator idGenerator,
			ExpirationJobProperties properties) {
		this(repository, transactionManager, idGenerator, properties.staleAfter(), InstanceIdentity.current());
	}

	JobRunPersistenceAdapter(ScheduledJobRunJpaRepository repository, PlatformTransactionManager transactionManager,
			IdGenerator idGenerator, Duration staleAfter, String instanceId) {
		this.repository = repository;
		this.requiresNew = new TransactionTemplate(transactionManager);
		this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.idGenerator = idGenerator;
		this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
		this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
	}

	@Override
	public Optional<UUID> tryStart(String jobName, LocalDate businessDate, Instant startedAt) {
		UUID runId = this.idGenerator.newId();
		try {
			this.requiresNew.executeWithoutResult(status -> this.repository.saveAndFlush(
					new ScheduledJobRunJpaEntity(runId, jobName, businessDate, this.instanceId, startedAt)));
			return Optional.of(runId);
		}
		catch (DataIntegrityViolationException ex) {
			if (!UK_JOB_DATE.equals(ConstraintViolations.constraintName(ex))) {
				throw ex;
			}
		}
		return Optional.ofNullable(this.requiresNew.execute(status -> takeOver(jobName, businessDate, startedAt)));
	}

	@Override
	public void finish(UUID runId, RunStatus status, RunSummary summary, Instant finishedAt) {
		this.requiresNew.executeWithoutResult(transaction -> this.repository.findById(runId)
			.orElseThrow(() -> new IllegalStateException("La ejecución " + runId + " no existe"))
			.finish(status.name(), summary.evaluated(), summary.issued(), summary.failed(), finishedAt));
	}

	private UUID takeOver(String jobName, LocalDate businessDate, Instant startedAt) {
		int updated = this.repository.takeOver(jobName, businessDate, this.instanceId, startedAt,
				startedAt.minus(this.staleAfter));
		if (updated == 0) {
			return null;
		}
		return this.repository.findByJobNameAndBusinessDate(jobName, businessDate)
			.map(ScheduledJobRunJpaEntity::getId)
			.orElse(null);
	}

}
