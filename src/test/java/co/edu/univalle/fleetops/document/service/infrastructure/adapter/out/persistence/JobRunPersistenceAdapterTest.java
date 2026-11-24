package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository.RunStatus;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository.RunSummary;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.ScheduledJobRunJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.ScheduledJobRunJpaRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.config.ExpirationJobProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobRunPersistenceAdapterTest {

	private static final String JOB = "document_expiration_check";

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

	private static final Instant NOW = Instant.parse("2026-11-23T06:00:00Z");

	private static final UUID RUN_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000a1");

	private static final UUID OTHER_RUN_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000a2");

	private static final Duration STALE_AFTER = Duration.ofMinutes(30);

	private static final String INSTANCE = "documents-1/42";

	@Mock
	private ScheduledJobRunJpaRepository repository;

	@Mock
	private PlatformTransactionManager transactionManager;

	@Mock
	private IdGenerator idGenerator;

	private JobRunPersistenceAdapter adapter;

	@BeforeEach
	void setUp() {
		this.adapter = new JobRunPersistenceAdapter(this.repository, this.transactionManager, this.idGenerator,
				STALE_AFTER, INSTANCE);
	}

	private static DataIntegrityViolationException violation(String constraint) {
		return new DataIntegrityViolationException("duplicado",
				new ConstraintViolationException("duplicado", new SQLException("23505"), constraint));
	}

	@Test
	void should_claim_the_date_by_inserting_a_running_execution_in_its_own_transaction() {
		// Arrange
		given(this.idGenerator.newId()).willReturn(RUN_ID);

		// Act
		Optional<UUID> claimed = this.adapter.tryStart(JOB, TODAY, NOW);

		// Assert
		assertThat(claimed).contains(RUN_ID);
		ArgumentCaptor<ScheduledJobRunJpaEntity> run = ArgumentCaptor.forClass(ScheduledJobRunJpaEntity.class);
		verify(this.repository).saveAndFlush(run.capture());
		assertThat(run.getValue()).extracting(ScheduledJobRunJpaEntity::getId, ScheduledJobRunJpaEntity::getJobName,
				ScheduledJobRunJpaEntity::getBusinessDate, ScheduledJobRunJpaEntity::getStatus,
				ScheduledJobRunJpaEntity::getInstanceId, ScheduledJobRunJpaEntity::getStartedAt,
				ScheduledJobRunJpaEntity::getFinishedAt)
			.containsExactly(RUN_ID, JOB, TODAY, "RUNNING", INSTANCE, NOW, null);
		ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
		verify(this.transactionManager).getTransaction(definition.capture());
		assertThat(definition.getValue().getPropagationBehavior())
			.isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Test
	void should_not_claim_a_date_that_another_instance_completed_or_is_running() {
		// Arrange (criterio 4)
		given(this.idGenerator.newId()).willReturn(RUN_ID);
		given(this.repository.saveAndFlush(any())).willThrow(violation(JobRunPersistenceAdapter.UK_JOB_DATE));
		given(this.repository.takeOver(JOB, TODAY, INSTANCE, NOW, NOW.minus(STALE_AFTER))).willReturn(0);

		// Act + Assert
		assertThat(this.adapter.tryStart(JOB, TODAY, NOW)).isEmpty();
		verify(this.repository, never()).findByJobNameAndBusinessDate(anyString(), any());
	}

	@Test
	void should_take_over_a_failed_or_abandoned_execution() {
		// Arrange: la instancia que la tenía se cayó o la ejecución falló
		given(this.idGenerator.newId()).willReturn(RUN_ID);
		given(this.repository.saveAndFlush(any())).willThrow(violation(JobRunPersistenceAdapter.UK_JOB_DATE));
		given(this.repository.takeOver(JOB, TODAY, INSTANCE, NOW, NOW.minus(STALE_AFTER))).willReturn(1);
		given(this.repository.findByJobNameAndBusinessDate(JOB, TODAY))
			.willReturn(Optional.of(new ScheduledJobRunJpaEntity(OTHER_RUN_ID, JOB, TODAY, INSTANCE, NOW)));

		// Act + Assert: se reutiliza la fila existente
		assertThat(this.adapter.tryStart(JOB, TODAY, NOW)).contains(OTHER_RUN_ID);
		verify(this.transactionManager, atLeastOnce()).rollback(any());
	}

	@Test
	void should_return_empty_if_the_taken_over_execution_disappeared() {
		given(this.idGenerator.newId()).willReturn(RUN_ID);
		given(this.repository.saveAndFlush(any())).willThrow(violation(JobRunPersistenceAdapter.UK_JOB_DATE));
		given(this.repository.takeOver(JOB, TODAY, INSTANCE, NOW, NOW.minus(STALE_AFTER))).willReturn(1);
		given(this.repository.findByJobNameAndBusinessDate(JOB, TODAY)).willReturn(Optional.empty());

		assertThat(this.adapter.tryStart(JOB, TODAY, NOW)).isEmpty();
	}

	@Test
	void should_propagate_other_integrity_errors() {
		// Arrange
		given(this.idGenerator.newId()).willReturn(RUN_ID);
		DataIntegrityViolationException other = violation("ck_scheduled_job_runs_status");
		given(this.repository.saveAndFlush(any())).willThrow(other);

		// Act + Assert
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.adapter.tryStart(JOB, TODAY, NOW))
			.isSameAs(other);
		verify(this.repository, never()).takeOver(any(), any(), any(), any(), any());
	}

	@Test
	void should_close_the_execution_with_its_summary() {
		// Arrange
		ScheduledJobRunJpaEntity run = new ScheduledJobRunJpaEntity(RUN_ID, JOB, TODAY, INSTANCE, NOW);
		given(this.repository.findById(RUN_ID)).willReturn(Optional.of(run));
		Instant finished = NOW.plusSeconds(9);

		// Act
		this.adapter.finish(RUN_ID, RunStatus.COMPLETED, new RunSummary(120, 15, 2), finished);

		// Assert (criterio 7)
		assertThat(run).extracting(ScheduledJobRunJpaEntity::getStatus, ScheduledJobRunJpaEntity::getEvaluatedCount,
				ScheduledJobRunJpaEntity::getIssuedCount, ScheduledJobRunJpaEntity::getFailedCount,
				ScheduledJobRunJpaEntity::getFinishedAt)
			.containsExactly("COMPLETED", 120, 15, 2, finished);
	}

	@Test
	void should_fail_to_close_a_missing_execution() {
		given(this.repository.findById(RUN_ID)).willReturn(Optional.empty());
		RunSummary summary = new RunSummary(0, 0, 0);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.adapter.finish(RUN_ID, RunStatus.FAILED, summary, NOW))
			.withMessageContaining(RUN_ID.toString());
	}

	@Test
	void should_build_from_the_job_properties() {
		JobRunPersistenceAdapter fromProperties = new JobRunPersistenceAdapter(this.repository,
				this.transactionManager, this.idGenerator, new ExpirationJobProperties(true, "0 0 1 * * *",
						STALE_AFTER));

		assertThat(fromProperties).isNotNull();
		assertThatNullPointerException().isThrownBy(() -> new JobRunPersistenceAdapter(this.repository,
				this.transactionManager, this.idGenerator, null, INSTANCE)).withMessage("staleAfter");
		assertThatNullPointerException().isThrownBy(() -> new JobRunPersistenceAdapter(this.repository,
				this.transactionManager, this.idGenerator, STALE_AFTER, null)).withMessage("instanceId");
	}

}
