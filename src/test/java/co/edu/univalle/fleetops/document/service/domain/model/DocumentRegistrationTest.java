package co.edu.univalle.fleetops.document.service.domain.model;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DocumentRegistrationTest {

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final OwnerRef OWNER = new OwnerRef(OwnerType.DRIVER,
			UUID.fromString("01960000-0000-7000-8000-000000000101"));

	private static final DocumentNumber NUMBER = new DocumentNumber("LIC-1020304050");

	private static final ValidityPeriod VALIDITY = new ValidityPeriod(LocalDate.of(2024, 3, 1),
			LocalDate.of(2034, 3, 1));

	@Test
	void should_keep_registration_data_when_created() {
		// Arrange
		LicenseCategory category = new LicenseCategory("C1");

		// Act
		DocumentRegistration registration = new DocumentRegistration(DOCUMENT_ID, OWNER, NUMBER, "Tránsito",
				VALIDITY, category);

		// Assert
		assertThat(registration.documentId()).isEqualTo(DOCUMENT_ID);
		assertThat(registration.owner()).isEqualTo(OWNER);
		assertThat(registration.documentNumber()).isEqualTo(NUMBER);
		assertThat(registration.issuingEntity()).isEqualTo("Tránsito");
		assertThat(registration.validity()).isEqualTo(VALIDITY);
		assertThat(registration.licenseCategory()).isEqualTo(category);
	}

	@Test
	void should_reject_missing_document_id() {
		assertThatNullPointerException()
			.isThrownBy(() -> new DocumentRegistration(null, OWNER, NUMBER, "Tránsito", VALIDITY, null))
			.withMessage("documentId");
	}

	@Test
	void should_reject_missing_owner() {
		assertThatNullPointerException()
			.isThrownBy(() -> new DocumentRegistration(DOCUMENT_ID, null, NUMBER, "Tránsito", VALIDITY, null))
			.withMessage("owner");
	}

	@Test
	void should_reject_missing_document_number() {
		assertThatNullPointerException()
			.isThrownBy(() -> new DocumentRegistration(DOCUMENT_ID, OWNER, null, "Tránsito", VALIDITY, null))
			.withMessage("documentNumber");
	}

	@Test
	void should_reject_missing_validity() {
		assertThatNullPointerException()
			.isThrownBy(() -> new DocumentRegistration(DOCUMENT_ID, OWNER, NUMBER, "Tránsito", null, null))
			.withMessage("validity");
	}

}
