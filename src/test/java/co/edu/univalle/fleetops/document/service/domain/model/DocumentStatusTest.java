package co.edu.univalle.fleetops.document.service.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStatusTest {

	@ParameterizedTest
	@CsvSource({ "VALID, true", "EXPIRING_SOON, true", "EXPIRED, true", "REPLACED, false", "CANCELLED, false" })
	void should_be_active_only_in_valid_expiring_soon_and_expired(DocumentStatus status, boolean expected) {
		// Act
		boolean active = status.isActive();

		// Assert
		assertThat(active).isEqualTo(expected);
	}

}
