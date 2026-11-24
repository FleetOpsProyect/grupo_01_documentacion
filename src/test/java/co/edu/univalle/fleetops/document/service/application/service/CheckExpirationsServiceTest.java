package co.edu.univalle.fleetops.document.service.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase.DocumentExpirationOutcome;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult.DocumentFailure;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository.RunStatus;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository.RunSummary;
import co.edu.univalle.fleetops.document.service.domain.service.AlertThresholdPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CheckExpirationsServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

	private static final Instant STARTED = Instant.parse("2026-11-23T06:00:00Z");

	private static final Instant FINISHED = Instant.parse("2026-11-23T06:00:09Z");

	private static final UUID RUN_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000a1");

	private static final UUID FIRST = UUID.fromString("019a2f4e-0000-7000-8000-000000000001");

	private static final UUID SECOND = UUID.fromString("019a2f4e-0000-7000-8000-000000000002");

	private static final UUID THIRD = UUID.fromString("019a2f4e-0000-7000-8000-000000000003");

	@Mock
	private JobRunRepository jobRuns;

	@Mock
	private DocumentRepository documents;

	@Mock
	private EvaluateDocumentExpirationUseCase evaluator;

	@Mock
	private BusinessClock clock;

	private CheckExpirationsService service() {
		return new CheckExpirationsService(this.jobRuns, this.documents, this.evaluator, this.clock,
				new AlertThresholdPolicy(List.of(30, 15, 7, 1)));
	}

	private void givenClaimedRun() {
		given(this.clock.today()).willReturn(TODAY);
		given(this.clock.now()).willReturn(STARTED, FINISHED);
		given(this.jobRuns.tryStart(CheckExpirationsService.JOB_NAME, TODAY, STARTED)).willReturn(Optional.of(RUN_ID));
	}

	@Test
	void should_evaluate_every_candidate_within_the_largest_threshold_and_close_the_run() {
		// Arrange
		givenClaimedRun();
		given(this.documents.findExpirationCandidateIds(TODAY, TODAY.plusDays(30)))
			.willReturn(List.of(FIRST, SECOND, THIRD));
		given(this.evaluator.evaluate(FIRST, TODAY, RUN_ID))
			.willReturn(DocumentExpirationOutcome.evaluated(true, true));
		given(this.evaluator.evaluate(SECOND, TODAY, RUN_ID))
			.willReturn(DocumentExpirationOutcome.evaluated(false, false));
		given(this.evaluator.evaluate(THIRD, TODAY, RUN_ID)).willReturn(DocumentExpirationOutcome.skippedOutcome());

		// Act
		ExpirationCheckResult result = service().checkExpirations();

		// Assert
		assertThat(result).isEqualTo(new ExpirationCheckResult(RUN_ID, TODAY, true, 2, 1, List.of()));
		verify(this.jobRuns).finish(RUN_ID, RunStatus.COMPLETED, new RunSummary(2, 1, 0), FINISHED);
	}

	@Test
	void should_continue_after_a_failed_document_and_report_it() {
		// Arrange (criterio 7)
		givenClaimedRun();
		given(this.documents.findExpirationCandidateIds(TODAY, TODAY.plusDays(30))).willReturn(List.of(FIRST, SECOND,
				THIRD));
		given(this.evaluator.evaluate(FIRST, TODAY, RUN_ID)).willThrow(new IllegalStateException("bloqueo"));
		given(this.evaluator.evaluate(SECOND, TODAY, RUN_ID)).willThrow(new IllegalStateException());
		given(this.evaluator.evaluate(THIRD, TODAY, RUN_ID))
			.willReturn(DocumentExpirationOutcome.evaluated(true, true));

		// Act
		ExpirationCheckResult result = service().checkExpirations();

		// Assert
		assertThat(result.evaluated()).isEqualTo(3);
		assertThat(result.issued()).isEqualTo(1);
		assertThat(result.failed()).isEqualTo(2);
		assertThat(result.failures()).containsExactly(new DocumentFailure(FIRST, "IllegalStateException: bloqueo"),
				new DocumentFailure(SECOND, "IllegalStateException"));
		verify(this.jobRuns).finish(RUN_ID, RunStatus.COMPLETED, new RunSummary(3, 1, 2), FINISHED);
	}

	@Test
	void should_do_nothing_when_the_date_was_already_checked() {
		// Arrange (criterio 4): otra instancia o una pasada anterior ya la verificó
		given(this.clock.today()).willReturn(TODAY);
		given(this.clock.now()).willReturn(STARTED);
		given(this.jobRuns.tryStart(CheckExpirationsService.JOB_NAME, TODAY, STARTED)).willReturn(Optional.empty());

		// Act
		ExpirationCheckResult result = service().checkExpirations();

		// Assert
		assertThat(result).isEqualTo(ExpirationCheckResult.alreadyRan(TODAY));
		assertThat(result.executed()).isFalse();
		verifyNoInteractions(this.documents, this.evaluator);
		verify(this.jobRuns, never()).finish(any(), any(), any(), any());
	}

	@Test
	void should_mark_the_run_as_failed_when_the_candidates_cannot_be_read() {
		// Arrange
		givenClaimedRun();
		IllegalStateException databaseDown = new IllegalStateException("base de datos caída");
		given(this.documents.findExpirationCandidateIds(TODAY, TODAY.plusDays(30))).willThrow(databaseDown);

		// Act + Assert
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> service().checkExpirations())
			.isSameAs(databaseDown);
		verify(this.jobRuns).finish(RUN_ID, RunStatus.FAILED, new RunSummary(0, 0, 0), FINISHED);
	}

	@Test
	void should_reject_missing_dependencies() {
		AlertThresholdPolicy policy = new AlertThresholdPolicy(List.of(30));
		assertThatNullPointerException()
			.isThrownBy(() -> new CheckExpirationsService(null, this.documents, this.evaluator, this.clock, policy))
			.withMessage("jobRuns");
		assertThatNullPointerException()
			.isThrownBy(() -> new CheckExpirationsService(this.jobRuns, null, this.evaluator, this.clock, policy))
			.withMessage("documents");
		assertThatNullPointerException()
			.isThrownBy(() -> new CheckExpirationsService(this.jobRuns, this.documents, null, this.clock, policy))
			.withMessage("evaluator");
		assertThatNullPointerException()
			.isThrownBy(() -> new CheckExpirationsService(this.jobRuns, this.documents, this.evaluator, null, policy))
			.withMessage("clock");
		assertThatNullPointerException()
			.isThrownBy(
					() -> new CheckExpirationsService(this.jobRuns, this.documents, this.evaluator, this.clock, null))
			.withMessage("alertPolicy");
	}

}
