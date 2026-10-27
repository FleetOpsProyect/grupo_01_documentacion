package co.edu.univalle.fleetops.document.service.domain.service;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class StatusEvaluationTest {

	@Test
	void should_keep_status_and_days_remaining() {
		// Act
		StatusEvaluation evaluation = new StatusEvaluation(DocumentStatus.EXPIRING_SOON, 7);

		// Assert
		assertThat(evaluation.status()).isEqualTo(DocumentStatus.EXPIRING_SOON);
		assertThat(evaluation.daysRemaining()).isEqualTo(7);
	}

	@Test
	void should_reject_a_missing_status() {
		assertThatNullPointerException().isThrownBy(() -> new StatusEvaluation(null, 7)).withMessage("status");
	}

}
