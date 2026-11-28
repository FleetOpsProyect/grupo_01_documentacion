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
import org.junit.jupiter.params.provider.EnumSource;

import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpired;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentRegistered;
import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;
import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;
import co.edu.univalle.fleetops.document.service.domain.exception.InvalidCancellationReasonException;
import co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException;
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
		void should_register_an_already_expired_driver_license_and_also_emit_document_expired() {
			// Arrange
			LocalDate yesterday = TODAY.minusDays(1);
			DocumentRegistration registration = registration(DRIVER, yesterday, new LicenseCategory("C1"));

			// Act
			Document document = Document.register(registration, DRIVER_LICENSE, DocumentTest.this.calculator, TODAY,
					NOW);

			// Assert (HU-DOC-02, criterio 6)
			assertThat(document.status()).isEqualTo(DocumentStatus.EXPIRED);
			List<DomainEvent> events = document.pullDomainEvents();
			assertThat(events).hasSize(2);
			assertThat(events.get(0)).isInstanceOf(DocumentRegistered.class);
			assertThat(events.get(1))
				.isEqualTo(new DocumentExpired(DOCUMENT_ID, DocumentTypeCode.DRIVER_LICENSE, DRIVER, yesterday, NOW));
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
		void should_reject_a_vehicle_document_for_a_driver() {
			assertThatExceptionOfType(BusinessRuleViolationException.class)
				.isThrownBy(() -> Document.register(registration(DRIVER, LocalDate.of(2027, 9, 30), null), SOAT,
						DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("El tipo de documento no corresponde al tipo de propietario");
		}

		@Test
		void should_reject_a_driver_license_for_a_vehicle() {
			assertThatExceptionOfType(BusinessRuleViolationException.class)
				.isThrownBy(() -> Document.register(
						registration(VEHICLE, LocalDate.of(2034, 3, 1), new LicenseCategory("C1")), DRIVER_LICENSE,
						DocumentTest.this.calculator, TODAY, NOW))
				.withMessage("El tipo de documento no corresponde al tipo de propietario");
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
	class WhenAnActiveDocumentAlreadyExists {

		@Test
		void should_ask_the_driver_to_renew_the_license() {
			assertThat(Document.activeDocumentConflict(DocumentTypeCode.DRIVER_LICENSE))
				.isInstanceOf(DomainConflictException.class)
				.hasMessage("El conductor ya tiene una licencia vigente; use la renovación");
		}

		@Test
		void should_ask_the_owner_to_renew_a_vehicle_document() {
			assertThat(Document.activeDocumentConflict(DocumentTypeCode.SOAT))
				.hasMessage("El propietario ya tiene un documento SOAT activo. Para cambiarlo use la renovación");
		}

		@Test
		void should_reject_a_missing_document_type() {
			assertThatNullPointerException().isThrownBy(() -> Document.activeDocumentConflict(null))
				.withMessage("documentType");
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

	@Nested
	class WhenCancellingADocument {

		private static final Instant CANCELLATION_TIME =
				Instant.parse("2026-11-27T15:00:00Z");

		private static final String REASON = "Documento registrado por error";

		private static final UUID PREVIOUS_DOCUMENT_ID =
				UUID.fromString("01960000-0000-7000-8000-000000000002");

		@ParameterizedTest
		@EnumSource(value = DocumentStatus.class,
				names = { "VALID", "EXPIRING_SOON", "EXPIRED" })
		void should_cancel_an_active_document(DocumentStatus status) {
			// Arrange
			Document original = documentWithStatus(status);

			// Act
			Document cancelled = original.cancel(REASON, CANCELLATION_TIME);

			// Assert
			assertThat(cancelled).isNotSameAs(original);
			assertThat(cancelled.status()).isEqualTo(DocumentStatus.CANCELLED);
			assertThat(cancelled.cancellationReason()).isEqualTo(REASON);
			assertThat(cancelled.cancelledAt()).isEqualTo(CANCELLATION_TIME);
			assertThat(cancelled.updatedAt()).isEqualTo(CANCELLATION_TIME);

			assertThat(cancelled.id()).isEqualTo(original.id());
			assertThat(cancelled.documentType()).isEqualTo(original.documentType());
			assertThat(cancelled.owner()).isEqualTo(original.owner());
			assertThat(cancelled.documentNumber()).isEqualTo(original.documentNumber());
			assertThat(cancelled.issuingEntity()).isEqualTo(original.issuingEntity());
			assertThat(cancelled.validity()).isEqualTo(original.validity());
			assertThat(cancelled.licenseCategory()).isEqualTo(original.licenseCategory());
			assertThat(cancelled.createdAt()).isEqualTo(original.createdAt());
			assertThat(cancelled.previousDocumentId()).isEqualTo(PREVIOUS_DOCUMENT_ID);

			assertThat(original.status()).isEqualTo(status);
			assertThat(original.cancellationReason()).isNull();
			assertThat(original.cancelledAt()).isNull();
			assertThat(original.updatedAt()).isEqualTo(NOW);

			assertThat(cancelled.pullDomainEvents()).isEmpty();
			assertThat(cancelled.status().reservesDocumentNumber()).isFalse();
		}

		@ParameterizedTest
		@EnumSource(value = DocumentStatus.class,
				names = { "REPLACED", "CANCELLED" })
		void should_reject_cancellation_of_a_terminal_document(DocumentStatus status) {
			Document original = documentWithStatus(status);

			assertThatExceptionOfType(DomainConflictException.class)
					.isThrownBy(() -> original.cancel(REASON, CANCELLATION_TIME))
					.withMessage("El documento no se puede modificar en su estado actual");
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "   ", "123456789" })
		void should_reject_an_invalid_cancellation_reason(String reason) {
			Document original = documentWithStatus(DocumentStatus.VALID);

			assertThatExceptionOfType(InvalidCancellationReasonException.class)
					.isThrownBy(() -> original.cancel(reason, CANCELLATION_TIME))
					.withMessage(
							"El motivo de anulación es obligatorio y debe tener entre 10 y 200 caracteres");
		}

		@Test
		void should_reject_a_reason_longer_than_200_characters() {
			Document original = documentWithStatus(DocumentStatus.VALID);

			assertThatExceptionOfType(InvalidCancellationReasonException.class)
					.isThrownBy(() -> original.cancel("A".repeat(201), CANCELLATION_TIME))
					.withMessage(
							"El motivo de anulación es obligatorio y debe tener entre 10 y 200 caracteres");
		}

		@ParameterizedTest
		@ValueSource(ints = { 10, 200 })
		void should_accept_the_cancellation_reason_limits(int length) {
			Document original = documentWithStatus(DocumentStatus.VALID);
			String reason = "A".repeat(length);

			Document cancelled = original.cancel(reason, CANCELLATION_TIME);

			assertThat(cancelled.cancellationReason()).isEqualTo(reason);
			assertThat(cancelled.status()).isEqualTo(DocumentStatus.CANCELLED);
		}

		@Test
		void should_strip_the_cancellation_reason() {
			Document original = documentWithStatus(DocumentStatus.VALID);

			Document cancelled = original.cancel("  " + REASON + "  ", CANCELLATION_TIME);

			assertThat(cancelled.cancellationReason()).isEqualTo(REASON);
		}

		@Test
		void should_reject_a_missing_cancellation_instant() {
			Document original = documentWithStatus(DocumentStatus.VALID);

			assertThatNullPointerException()
					.isThrownBy(() -> original.cancel(REASON, null))
					.withMessage("now");
		}

		private Document documentWithStatus(DocumentStatus status) {
			DocumentRegistration data = registration(
					DRIVER, LocalDate.of(2034, 3, 1), new LicenseCategory("B1"));

			return Document.reconstitute(
					data,
					DocumentTypeCode.DRIVER_LICENSE,
					status,
					NOW,
					NOW,
					PREVIOUS_DOCUMENT_ID,
					null,
					null);
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
