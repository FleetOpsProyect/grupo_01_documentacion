package co.edu.univalle.fleetops.document.service.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class DocumentNumberTest {

	@Test
	void should_strip_surrounding_spaces_when_created() {
		// Act
		DocumentNumber number = new DocumentNumber("  AT-1300-0045-2026 ");

		// Assert
		assertThat(number.value()).isEqualTo("AT-1300-0045-2026");
	}

	@Test
	void should_accept_fifty_characters() {
		// Arrange
		String fifty = "A".repeat(50);

		// Act
		DocumentNumber number = new DocumentNumber(fifty);

		// Assert
		assertThat(number.value()).hasSize(50);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void should_reject_missing_number(String value) {
		assertThatExceptionOfType(BusinessRuleViolationException.class).isThrownBy(() -> new DocumentNumber(value))
			.withMessage("document_number es obligatorio");
	}

	@Test
	void should_reject_more_than_fifty_characters() {
		assertThatExceptionOfType(BusinessRuleViolationException.class)
			.isThrownBy(() -> new DocumentNumber("A".repeat(51)))
			.withMessage("document_number no puede tener más de 50 caracteres");
	}

	@Test
	void should_mask_all_but_last_four_characters() {
		// Arrange
		DocumentNumber number = new DocumentNumber("LIC-1020304050");

		// Act
		String masked = number.masked();

		// Assert
		assertThat(masked).isEqualTo("**********4050");
	}

	@ParameterizedTest
	@ValueSource(strings = { "1234", "12" })
	void should_mask_everything_when_number_has_four_characters_or_less(String value) {
		// Act
		String masked = new DocumentNumber(value).masked();

		// Assert
		assertThat(masked).isEqualTo("*".repeat(value.length()));
	}

	@Test
	void should_not_expose_the_full_number_in_to_string() {
		// Arrange
		DocumentNumber number = new DocumentNumber("LIC-1020304050");

		// Act
		String text = number.toString();

		// Assert
		assertThat(text).isEqualTo("**********4050").doesNotContain("LIC-102");
	}

}
