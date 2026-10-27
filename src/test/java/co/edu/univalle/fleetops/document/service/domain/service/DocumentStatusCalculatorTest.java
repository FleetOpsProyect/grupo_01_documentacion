package co.edu.univalle.fleetops.document.service.domain.service;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DocumentStatusCalculatorTest {

	private static final LocalDate EXPIRATION = LocalDate.of(2026, 10, 20);

	private final DocumentStatusCalculator calculator = new DocumentStatusCalculator(30);

	@Test
	void should_be_expiring_soon_with_zero_days_on_the_expiration_day() {
		// Act
		StatusEvaluation evaluation = this.calculator.evaluate(EXPIRATION, LocalDate.of(2026, 10, 20));

		// Assert (DAS sec. 4.2: vencimiento el 20 de octubre, EXPIRING_SOON ese día)
		assertThat(evaluation).isEqualTo(new StatusEvaluation(DocumentStatus.EXPIRING_SOON, 0));
	}

	@Test
	void should_be_expired_from_the_day_after_the_expiration_day() {
		// Act
		StatusEvaluation evaluation = this.calculator.evaluate(EXPIRATION, LocalDate.of(2026, 10, 21));

		// Assert (DAS sec. 4.2: EXPIRED desde el 21)
		assertThat(evaluation).isEqualTo(new StatusEvaluation(DocumentStatus.EXPIRED, -1));
	}

	@ParameterizedTest
	@CsvSource({ "2026-09-20, EXPIRING_SOON, 30", "2026-09-19, VALID, 31", "2026-10-19, EXPIRING_SOON, 1" })
	void should_use_the_highest_threshold_as_the_limit_between_valid_and_expiring_soon(LocalDate today,
			DocumentStatus expectedStatus, int expectedDays) {
		// Act
		StatusEvaluation evaluation = this.calculator.evaluate(EXPIRATION, today);

		// Assert
		assertThat(evaluation.status()).isEqualTo(expectedStatus);
		assertThat(evaluation.daysRemaining()).isEqualTo(expectedDays);
	}

	@Test
	void should_be_valid_without_days_remaining_when_the_document_does_not_expire() {
		// Act
		StatusEvaluation evaluation = this.calculator.evaluate(null, LocalDate.of(2026, 10, 21));

		// Assert
		assertThat(evaluation).isEqualTo(new StatusEvaluation(DocumentStatus.VALID, null));
	}

	@Test
	void should_reject_a_missing_business_date() {
		assertThatNullPointerException().isThrownBy(() -> this.calculator.evaluate(EXPIRATION, null))
			.withMessage("today");
	}

	@Test
	void should_reject_a_negative_threshold() {
		assertThatIllegalArgumentException().isThrownBy(() -> new DocumentStatusCalculator(-1))
			.withMessage("El umbral mayor de alerta no puede ser negativo");
	}

	@Test
	void should_accept_a_zero_threshold() {
		// Arrange
		DocumentStatusCalculator zero = new DocumentStatusCalculator(0);

		// Act
		StatusEvaluation evaluation = zero.evaluate(EXPIRATION, LocalDate.of(2026, 10, 19));

		// Assert
		assertThat(evaluation.status()).isEqualTo(DocumentStatus.VALID);
	}

}
