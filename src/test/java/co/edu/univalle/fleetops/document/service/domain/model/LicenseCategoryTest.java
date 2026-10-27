package co.edu.univalle.fleetops.document.service.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LicenseCategoryTest {

	@ParameterizedTest
	@ValueSource(strings = { "C1", "B", " A2 " })
	void should_accept_a_single_category(String value) {
		// Act
		LicenseCategory category = new LicenseCategory(value);

		// Assert
		assertThat(category.value()).isEqualTo(value.strip());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "  " })
	void should_reject_missing_category(String value) {
		assertThatExceptionOfType(BusinessRuleViolationException.class).isThrownBy(() -> new LicenseCategory(value))
			.withMessage("license_category es obligatoria");
	}

	@ParameterizedTest
	@ValueSource(strings = { "B1,C1", "c1", "C12", "1C" })
	void should_reject_more_than_one_category_or_invalid_format(String value) {
		assertThatExceptionOfType(BusinessRuleViolationException.class).isThrownBy(() -> new LicenseCategory(value))
			.withMessageStartingWith("license_category debe ser una sola categoría");
	}

}
