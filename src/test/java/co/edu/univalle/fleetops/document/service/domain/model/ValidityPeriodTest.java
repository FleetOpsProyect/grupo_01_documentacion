package co.edu.univalle.fleetops.document.service.domain.model;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ValidityPeriodTest {

	private static final LocalDate ISSUE = LocalDate.of(2026, 10, 1);

	@Test
	void should_have_expiration_when_expiration_date_is_after_issue_date() {
		// Act
		ValidityPeriod period = new ValidityPeriod(ISSUE, LocalDate.of(2027, 9, 30));

		// Assert
		assertThat(period.issueDate()).isEqualTo(ISSUE);
		assertThat(period.expirationDate()).isEqualTo(LocalDate.of(2027, 9, 30));
		assertThat(period.hasExpiration()).isTrue();
	}

	@Test
	void should_have_no_expiration_when_expiration_date_is_empty() {
		// Act
		ValidityPeriod period = new ValidityPeriod(ISSUE, null);

		// Assert
		assertThat(period.hasExpiration()).isFalse();
	}

	@Test
	void should_reject_missing_issue_date() {
		assertThatExceptionOfType(BusinessRuleViolationException.class)
			.isThrownBy(() -> new ValidityPeriod(null, LocalDate.of(2027, 9, 30)))
			.withMessage("issue_date es obligatoria");
	}

	@Test
	void should_reject_expiration_date_equal_to_issue_date() {
		assertThatExceptionOfType(BusinessRuleViolationException.class)
			.isThrownBy(() -> new ValidityPeriod(ISSUE, ISSUE))
			.withMessage("expiration_date debe ser posterior a issue_date");
	}

	@Test
	void should_reject_expiration_date_before_issue_date() {
		assertThatExceptionOfType(BusinessRuleViolationException.class)
			.isThrownBy(() -> new ValidityPeriod(ISSUE, ISSUE.minusDays(1)));
	}

}
