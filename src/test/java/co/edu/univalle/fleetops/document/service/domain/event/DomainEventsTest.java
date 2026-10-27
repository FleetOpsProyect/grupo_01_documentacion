package co.edu.univalle.fleetops.document.service.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.LicenseCategory;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.model.ValidityPeriod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DomainEventsTest {

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final OwnerRef DRIVER = new OwnerRef(OwnerType.DRIVER,
			UUID.fromString("01960000-0000-7000-8000-000000000101"));

	private static final ValidityPeriod VALIDITY = new ValidityPeriod(LocalDate.of(2024, 3, 1),
			LocalDate.of(2034, 3, 1));

	private static final Instant NOW = Instant.parse("2026-10-26T15:04:05Z");

	@Nested
	class DocumentRegisteredEvent {

		@Test
		void should_keep_the_registered_document_data() {
			// Act
			DocumentRegistered event = new DocumentRegistered(DOCUMENT_ID, DocumentTypeCode.DRIVER_LICENSE, DRIVER,
					"Tránsito", VALIDITY, new LicenseCategory("C1"), DocumentStatus.VALID, NOW);

			// Assert
			assertThat(event.documentId()).isEqualTo(DOCUMENT_ID);
			assertThat(event.documentType()).isEqualTo(DocumentTypeCode.DRIVER_LICENSE);
			assertThat(event.owner()).isEqualTo(DRIVER);
			assertThat(event.issuingEntity()).isEqualTo("Tránsito");
			assertThat(event.validity()).isEqualTo(VALIDITY);
			assertThat(event.licenseCategory()).isEqualTo(new LicenseCategory("C1"));
			assertThat(event.status()).isEqualTo(DocumentStatus.VALID);
			assertThat(event.occurredAt()).isEqualTo(NOW);
		}

		@Test
		void should_reject_missing_required_data() {
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentRegistered(null, DocumentTypeCode.SOAT, DRIVER, "x", VALIDITY, null,
						DocumentStatus.VALID, NOW))
				.withMessage("documentId");
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentRegistered(DOCUMENT_ID, null, DRIVER, "x", VALIDITY, null,
						DocumentStatus.VALID, NOW))
				.withMessage("documentType");
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentRegistered(DOCUMENT_ID, DocumentTypeCode.SOAT, null, "x", VALIDITY, null,
						DocumentStatus.VALID, NOW))
				.withMessage("owner");
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentRegistered(DOCUMENT_ID, DocumentTypeCode.SOAT, DRIVER, "x", null, null,
						DocumentStatus.VALID, NOW))
				.withMessage("validity");
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentRegistered(DOCUMENT_ID, DocumentTypeCode.SOAT, DRIVER, "x", VALIDITY,
						null, null, NOW))
				.withMessage("status");
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentRegistered(DOCUMENT_ID, DocumentTypeCode.SOAT, DRIVER, "x", VALIDITY,
						null, DocumentStatus.VALID, null))
				.withMessage("occurredAt");
		}

	}

	@Nested
	class DocumentExpiredEvent {

		@Test
		void should_keep_the_expired_document_data() {
			// Act
			DocumentExpired event = new DocumentExpired(DOCUMENT_ID, DocumentTypeCode.DRIVER_LICENSE, DRIVER,
					LocalDate.of(2026, 10, 20), NOW);

			// Assert
			assertThat(event.documentId()).isEqualTo(DOCUMENT_ID);
			assertThat(event.documentType()).isEqualTo(DocumentTypeCode.DRIVER_LICENSE);
			assertThat(event.owner()).isEqualTo(DRIVER);
			assertThat(event.expirationDate()).isEqualTo(LocalDate.of(2026, 10, 20));
			assertThat(event.occurredAt()).isEqualTo(NOW);
		}

		@Test
		void should_reject_missing_required_data() {
			LocalDate expiration = LocalDate.of(2026, 10, 20);
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentExpired(null, DocumentTypeCode.SOAT, DRIVER, expiration, NOW))
				.withMessage("documentId");
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentExpired(DOCUMENT_ID, null, DRIVER, expiration, NOW))
				.withMessage("documentType");
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentExpired(DOCUMENT_ID, DocumentTypeCode.SOAT, null, expiration, NOW))
				.withMessage("owner");
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentExpired(DOCUMENT_ID, DocumentTypeCode.SOAT, DRIVER, null, NOW))
				.withMessage("expirationDate");
			assertThatNullPointerException()
				.isThrownBy(() -> new DocumentExpired(DOCUMENT_ID, DocumentTypeCode.SOAT, DRIVER, expiration, null))
				.withMessage("occurredAt");
		}

	}

}
