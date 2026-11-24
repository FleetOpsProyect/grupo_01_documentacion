package co.edu.univalle.fleetops.document.service.application.port;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase.DocumentExpirationOutcome;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult.DocumentFailure;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository.ExpirationCandidate;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository.RunSummary;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Validaciones de los tipos que viajan por los puertos de HU-DOC-06.
 */
class ExpirationPortsTest {

	private static final UUID ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final LocalDate DATE = LocalDate.of(2026, 11, 23);

	private static final OwnerRef OWNER = new OwnerRef(OwnerType.DRIVER,
			UUID.fromString("01960000-0000-7000-8000-000000000101"));

	@Test
	void should_build_an_execution_result_with_an_immutable_failure_list() {
		List<DocumentFailure> failures = new java.util.ArrayList<>(List.of(new DocumentFailure(ID, "error")));

		ExpirationCheckResult result = new ExpirationCheckResult(ID, DATE, true, 1, 0, failures);
		failures.clear();

		assertThat(result.failed()).isEqualTo(1);
		assertThat(result.failures()).hasSize(1);
	}

	@Test
	void should_describe_a_date_that_was_already_checked() {
		ExpirationCheckResult result = ExpirationCheckResult.alreadyRan(DATE);

		assertThat(result.executed()).isFalse();
		assertThat(result.runId()).isNull();
		assertThat(result.failed()).isZero();
	}

	@Test
	void should_validate_the_execution_result() {
		List<DocumentFailure> none = List.of();
		assertThatNullPointerException().isThrownBy(() -> new ExpirationCheckResult(ID, null, true, 0, 0, none))
			.withMessage("businessDate");
		assertThatNullPointerException().isThrownBy(() -> new ExpirationCheckResult(ID, DATE, true, 0, 0, null))
			.withMessage("failures");
		assertThatNullPointerException().isThrownBy(() -> new ExpirationCheckResult(null, DATE, true, 0, 0, none))
			.withMessage("runId");
		assertThatNullPointerException().isThrownBy(() -> new DocumentFailure(null, "error"))
			.withMessage("documentId");
		assertThatNullPointerException().isThrownBy(() -> new DocumentFailure(ID, null)).withMessage("reason");
	}

	@Test
	void should_describe_the_outcome_of_a_document() {
		assertThat(DocumentExpirationOutcome.skippedOutcome())
			.isEqualTo(new DocumentExpirationOutcome(true, false, false));
		assertThat(DocumentExpirationOutcome.evaluated(true, false))
			.isEqualTo(new DocumentExpirationOutcome(false, true, false));
	}

	@Test
	void should_validate_an_expiration_candidate() {
		ExpirationCandidate candidate = new ExpirationCandidate(ID, DocumentTypeCode.DRIVER_LICENSE, OWNER, DATE,
				DocumentStatus.VALID);
		assertThat(candidate.owner()).isEqualTo(OWNER);

		assertThatNullPointerException().isThrownBy(
				() -> new ExpirationCandidate(null, DocumentTypeCode.SOAT, OWNER, DATE, DocumentStatus.VALID))
			.withMessage("documentId");
		assertThatNullPointerException()
			.isThrownBy(() -> new ExpirationCandidate(ID, null, OWNER, DATE, DocumentStatus.VALID))
			.withMessage("documentType");
		assertThatNullPointerException()
			.isThrownBy(() -> new ExpirationCandidate(ID, DocumentTypeCode.SOAT, null, DATE, DocumentStatus.VALID))
			.withMessage("owner");
		assertThatNullPointerException()
			.isThrownBy(() -> new ExpirationCandidate(ID, DocumentTypeCode.SOAT, OWNER, null, DocumentStatus.VALID))
			.withMessage("expirationDate");
		assertThatNullPointerException()
			.isThrownBy(() -> new ExpirationCandidate(ID, DocumentTypeCode.SOAT, OWNER, DATE, null))
			.withMessage("status");
	}

	@Test
	void should_reject_negative_run_counters() {
		assertThat(new RunSummary(0, 0, 0).evaluated()).isZero();
		assertThatIllegalArgumentException().isThrownBy(() -> new RunSummary(-1, 0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new RunSummary(0, -1, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new RunSummary(0, 0, -1));
	}

}
