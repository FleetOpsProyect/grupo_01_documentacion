package co.edu.univalle.fleetops.document.service.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpired;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentRegistered;
import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;
import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DocumentTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

	private static final Instant NOW = Instant.parse("2026-10-26T15:04:05Z");

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final OwnerRef VEHICLE = new OwnerRef(OwnerType.VEHICLE,
			UUID.fromString("01960000-0000-7000-8000-000000000001"));

	private static final OwnerRef DRIVER = new OwnerRef(OwnerType.DRIVER,
			UUID.fromString("01960000-0000-7000-8000-000000000101"));

	private static final DocumentTypeRules SOAT = rules(DocumentTypeCode.SOAT, OwnerType.VEHICLE, true, true);

	private static final DocumentTypeRules PROPERTY_CARD = rules(DocumentTypeCode.PROPERTY_CARD, OwnerType.VEHICLE,
			false, true);

	private static final DocumentTypeRules DRIVER_LICENSE = rules(DocumentTypeCode.DRIVER_LICENSE, OwnerType.DRIVER,
			true, true);

	private final DocumentStatusCalculator calculator = new DocumentStatusCalculator(30);

	@Nested
	class WhenTheDataIsValid {

		@Test
		void should_register_a_valid_soat_and_emit_only_document_registered() {
			// Arrange
			DocumentRegistration registration = registration(VEHICLE, LocalDate.of(2027, 9, 30), null);

			// Act
			Document document = Document.register(registration, SOAT, DocumentTest.this.calculator, TODAY, NOW);

			// Assert
			assertThat(document.id()).isEqualTo(DOCUMENT_ID);
			assertThat(document.documentType()).isEqualTo(DocumentTypeCode.SOAT);
			assertThat(document.owner()).isEqualTo(VEHICLE);
			assertThat(document.documentNumber().value()).isEqualTo("AT-1300-0045-2026");
			assertThat(document.issuingEntity()).isEqualTo("Seguros Demo S.A.");
			assertThat(document.validity().expirationDate()).isEqualTo(LocalDate.of(2027, 9, 30));
			assertThat(document.licenseCategory()).isNull();
			assertThat(document.status()).isEqualTo(DocumentStatus.VALID);
			assertThat(document.createdAt()).isEqualTo(NOW);
			assertThat(document.updatedAt()).isEqualTo(NOW);
			assertThat(document.pullDomainEvents()).singleElement()
				.isEqualTo(new DocumentRegistered(DOCUMENT_ID, DocumentTypeCode.SOAT, VEHICLE, "Seguros Demo S.A.",
						registration.validity(), null, DocumentStatus.VALID, NOW));
		}

		@Test
		void should_register_an_already_expired_document_and_also_emit_document_expired() {
			// Arrange
			LocalDate yesterday = TODAY.minusDays(1);
			DocumentRegistration registration = registration(VEHICLE, yesterday, null);

			// Act
			Document document = Document.register(registration, SOAT, DocumentTest.this.calculator, TODAY, NOW);

			// Assert
			assertThat(document.status()).isEqualTo(DocumentStatus.EXPIRED);
			List<DomainEvent> events = document.pullDomainEvents();
			assertThat(events).hasSize(2);
			assertThat(events.get(0)).isInstanceOf(DocumentRegistered.class);
			assertThat(events.get(1))
				.isEqualTo(new DocumentExpired(DOCUMENT_ID, DocumentTypeCode.SOAT, VEHICLE, yesterday, NOW));
		}

		@Test
		void should_register_an_expiring_soon_document_without_document_expired() {
			// Arrange
			DocumentRegistration registration = registration(VEHICLE, TODAY.plusDays(10), null);

			// Act
			Document document = Document.register(registration, SOAT, DocumentTest.this.calculator, TODAY, NOW);

			// Assert
			assertThat(document.status()).isEqualTo(DocumentStatus.EXPIRING_SOON);
			assertThat(document.pullDomainEvents()).singleElement().isInstanceOf(DocumentRegistered.class);
		}

		@Test
		void should_register_a_property_card_without_expiration_as_valid() {
			// Arrange
			DocumentRegistration registration = registration(VEHICLE, null, null);

			// Act
			Document document = Document.register(registration, PROPERTY_CARD, DocumentTest.this.calculator, TODAY,
					NOW);

			// Assert
			assertThat(document.status()).isEqualTo(DocumentStatus.VALID);
			assertThat(document.validity().hasExpiration()).isFalse();
		}

		@Test
		void should_register_a_driver_license_with_its_single_category() {
			// Arrange
			DocumentRegistration registration = registration(DRIVER, LocalDate.of(2034, 3, 1),
					new LicenseCategory("C1"));

			// Act
			Document document = Document.register(registration, DRIVER_LICENSE, DocumentTest.this.calculator, TODAY,
					NOW);

			// Assert
			assertThat(document.licenseCategory()).isEqualTo(new LicenseCategory("C1"));
			assertThat(document.pullDomainEvents()).singleElement()
				.extracting(event -> ((DocumentRegistered) event).licenseCategory())
				.isEqualTo(new LicenseCategory("C1"));
		}

		@Test
		void should_strip_the_issuing_entity() {
			// Arrange
			DocumentRegistration registration = new DocumentRegistration(DOCUMENT_ID, VEHICLE,
					new DocumentNumber("AT-1"), "  Seguros Demo S.A.  ",
					new ValidityPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30)), null);

			// Act
			Document document = Document.register(registration, SOAT, DocumentTest.this.calculator, TODAY, NOW);

			// Assert
			assertThat(document.issuingEntity()).isEqualTo("Seguros Demo S.A.");
		}

		@Test
		void should_hand_over_domain_events_only_once() {
			// Arrange
			Document document = Document.register(registration(VEHICLE, LocalDate.of(2027, 9, 30), null), SOAT,
					DocumentTest.this.calculator, TODAY, NOW);
			document.pullDomainEvents();

			// Act
			List<DomainEvent> second = document.pullDomainEvents();

			// Assert
			assertThat(second).isEmpty();
		}

	}

	@Nested
	class WhenTheDataBreaksTheTypeRules {

		@Test
		void should_reject_a_disabled_document_type() {
			// Arrange
			DocumentTypeRules disabled = rules(DocumentTypeCode.SOAT, OwnerType.VEHICLE, true, false);

			// Act + Assert
			assertThatExceptionOfType(BusinessRuleViolationException.class)
				.isThrownBy(() -> Document.register(registration(VEHICLE, LocalDate.of(2027, 9, 30), null), disabled,
						DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("El tipo de documento SOAT no está habilitado");
		}

		@Test
		void should_reject_an_owner_that_does_not_match_the_type() {
			assertThatExceptionOfType(BusinessRuleViolationException.class)
				.isThrownBy(() -> Document.register(registration(DRIVER, LocalDate.of(2027, 9, 30), null), SOAT,
						DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("El tipo de documento SOAT solo se registra para un propietario VEHICLE");
		}

		@Test
		void should_reject_a_missing_expiration_date_when_the_type_expires() {
			assertThatExceptionOfType(BusinessRuleViolationException.class)
				.isThrownBy(() -> Document.register(registration(VEHICLE, null, null), SOAT,
						DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("expiration_date es obligatoria para el tipo SOAT");
		}

		@Test
		void should_reject_an_expiration_date_when_the_type_does_not_expire() {
			assertThatExceptionOfType(BusinessRuleViolationException.class)
				.isThrownBy(() -> Document.register(registration(VEHICLE, LocalDate.of(2030, 1, 1), null),
						PROPERTY_CARD, DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("El tipo PROPERTY_CARD no tiene vencimiento: expiration_date debe ir vacía");
		}

		@Test
		void should_reject_a_driver_license_without_category() {
			assertThatExceptionOfType(BusinessRuleViolationException.class)
				.isThrownBy(() -> Document.register(registration(DRIVER, LocalDate.of(2034, 3, 1), null),
						DRIVER_LICENSE, DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("license_category es obligatoria para el tipo DRIVER_LICENSE");
		}

		@Test
		void should_reject_a_category_in_a_vehicle_document() {
			assertThatExceptionOfType(BusinessRuleViolationException.class)
				.isThrownBy(() -> Document.register(
						registration(VEHICLE, LocalDate.of(2027, 9, 30), new LicenseCategory("B1")), SOAT,
						DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("license_category solo aplica a la licencia de conducción, no al tipo SOAT");
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "   " })
		void should_reject_a_missing_issuing_entity(String issuingEntity) {
			// Arrange
			DocumentRegistration registration = new DocumentRegistration(DOCUMENT_ID, VEHICLE,
					new DocumentNumber("AT-1"), issuingEntity,
					new ValidityPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30)), null);

			// Act + Assert
			assertThatExceptionOfType(BusinessRuleViolationException.class)
				.isThrownBy(() -> Document.register(registration, SOAT, DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("issuing_entity es obligatoria");
		}

		@Test
		void should_reject_an_issuing_entity_longer_than_150_characters() {
			// Arrange
			DocumentRegistration registration = new DocumentRegistration(DOCUMENT_ID, VEHICLE,
					new DocumentNumber("AT-1"), "E".repeat(151),
					new ValidityPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30)), null);

			// Act + Assert
			assertThatExceptionOfType(BusinessRuleViolationException.class)
				.isThrownBy(() -> Document.register(registration, SOAT, DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("issuing_entity no puede tener más de 150 caracteres");
		}

	}

	@Nested
	class WhenAnArgumentIsMissing {

		@Test
		void should_reject_missing_registration() {
			assertThatNullPointerException()
				.isThrownBy(() -> Document.register(null, SOAT, DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("registration");
		}

		@Test
		void should_reject_missing_rules() {
			assertThatNullPointerException()
				.isThrownBy(() -> Document.register(registration(VEHICLE, LocalDate.of(2027, 9, 30), null), null,
						DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("rules");
		}

		@Test
		void should_reject_missing_calculator() {
			assertThatNullPointerException()
				.isThrownBy(() -> Document.register(registration(VEHICLE, LocalDate.of(2027, 9, 30), null), SOAT, null,
						TODAY, NOW))
				.withMessage("calculator");
		}

		@Test
		void should_reject_missing_instant() {
			assertThatNullPointerException()
				.isThrownBy(() -> Document.register(registration(VEHICLE, LocalDate.of(2027, 9, 30), null), SOAT,
						DocumentTest.this.calculator, TODAY, null))
				.withMessage("now");
		}

	}

	private static DocumentTypeRules rules(DocumentTypeCode code, OwnerType ownerType, boolean hasExpiration,
			boolean active) {
		return new DocumentTypeRules(code, ownerType, hasExpiration, true, hasExpiration, active);
	}

	private static DocumentRegistration registration(OwnerRef owner, LocalDate expirationDate,
			LicenseCategory category) {
		return new DocumentRegistration(DOCUMENT_ID, owner, new DocumentNumber("AT-1300-0045-2026"),
				"Seguros Demo S.A.", new ValidityPeriod(LocalDate.of(2026, 10, 1), expirationDate), category);
	}

}
