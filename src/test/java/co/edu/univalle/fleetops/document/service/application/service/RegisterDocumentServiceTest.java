package co.edu.univalle.fleetops.document.service.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentCommand;
import co.edu.univalle.fleetops.document.service.application.port.out.AuditLogRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.CorrelationIdProvider;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentTypeRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OwnerReplicaRepository;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpired;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentRegistered;
import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;
import co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeRules;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RegisterDocumentServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

	private static final Instant NOW = Instant.parse("2026-10-26T15:04:05Z");

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID EVENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-00000000e001");

	private static final UUID SECOND_EVENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-00000000e002");

	private static final UUID CORRELATION_ID = UUID.fromString("6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");

	private static final UUID VEHICLE_ID = UUID.fromString("01960000-0000-7000-8000-000000000001");

	private static final UUID DRIVER_ID = UUID.fromString("01960000-0000-7000-8000-000000000101");

	private static final OwnerRef VEHICLE = new OwnerRef(OwnerType.VEHICLE, VEHICLE_ID);

	private static final DocumentTypeRules SOAT_RULES = new DocumentTypeRules(DocumentTypeCode.SOAT,
			OwnerType.VEHICLE, true, true, true, true);

	private static final DocumentTypeRules LICENSE_RULES = new DocumentTypeRules(DocumentTypeCode.DRIVER_LICENSE,
			OwnerType.DRIVER, true, true, true, true);

	@Mock
	private DocumentTypeRepository documentTypes;

	@Mock
	private OwnerReplicaRepository owners;

	@Mock
	private DocumentRepository documents;

	@Mock
	private AuditLogRepository auditLog;

	@Mock
	private OutboxRepository outbox;

	@Mock
	private IdGenerator idGenerator;

	@Mock
	private CorrelationIdProvider correlationIds;

	@Mock
	private BusinessClock clock;

	private RegisterDocumentService service;

	@BeforeEach
	void setUp() {
		this.service = new RegisterDocumentService(this.documentTypes, this.owners, this.documents, this.auditLog,
				this.outbox, this.idGenerator, this.correlationIds, this.clock, new DocumentStatusCalculator(30));
	}

	@Test
	void should_register_a_valid_soat_and_store_document_audit_and_registered_event() {
		// Arrange
		givenTheCatalogHas(SOAT_RULES);
		givenIds(DOCUMENT_ID, EVENT_ID);
		givenTheClock();
		given(this.owners.isActiveOwner(VEHICLE)).willReturn(true);
		given(this.correlationIds.currentCorrelationId()).willReturn(Optional.of(CORRELATION_ID));

		// Act
		DocumentView view = this.service.register(soatCommand("AT-1300-0045-2026", LocalDate.of(2027, 9, 30)));

		// Assert
		ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
		verify(this.documents).save(saved.capture());
		assertThat(saved.getValue().id()).isEqualTo(DOCUMENT_ID);
		verify(this.auditLog).recordRegistration(saved.getValue(), CORRELATION_ID);
		verify(this.outbox).append(eq(EVENT_ID), any(DocumentRegistered.class), eq(CORRELATION_ID));
		assertThat(view).isEqualTo(new DocumentView(DOCUMENT_ID, DocumentTypeCode.SOAT, OwnerType.VEHICLE,
				VEHICLE_ID, "AT-1300-0045-2026", "Seguros Demo S.A.", LocalDate.of(2026, 10, 1),
				LocalDate.of(2027, 9, 30), null, DocumentStatus.VALID, 339, NOW, NOW));
	}

	@Test
	void should_store_registered_and_expired_events_in_order_when_the_document_is_already_expired() {
		// Arrange
		givenTheCatalogHas(SOAT_RULES);
		givenIds(DOCUMENT_ID, EVENT_ID, SECOND_EVENT_ID);
		givenTheClock();
		given(this.owners.isActiveOwner(VEHICLE)).willReturn(true);
		given(this.correlationIds.currentCorrelationId()).willReturn(Optional.of(CORRELATION_ID));

		// Act
		DocumentView view = this.service.register(soatCommand("AT-1", TODAY.minusDays(1)));

		// Assert
		assertThat(view.status()).isEqualTo(DocumentStatus.EXPIRED);
		assertThat(view.daysRemaining()).isEqualTo(-1);
		InOrder order = inOrder(this.documents, this.outbox);
		order.verify(this.documents).save(any(Document.class));
		order.verify(this.outbox).append(eq(EVENT_ID), any(DocumentRegistered.class), eq(CORRELATION_ID));
		order.verify(this.outbox).append(eq(SECOND_EVENT_ID), any(DocumentExpired.class), eq(CORRELATION_ID));
	}

	@Test
	void should_register_a_driver_license_with_its_category() {
		// Arrange
		givenTheCatalogHas(LICENSE_RULES);
		givenIds(DOCUMENT_ID, EVENT_ID);
		givenTheClock();
		given(this.owners.isActiveOwner(new OwnerRef(OwnerType.DRIVER, DRIVER_ID))).willReturn(true);
		given(this.correlationIds.currentCorrelationId()).willReturn(Optional.empty());
		RegisterDocumentCommand command = new RegisterDocumentCommand(DocumentTypeCode.DRIVER_LICENSE,
				OwnerType.DRIVER, DRIVER_ID, "LIC-1020304050", "Secretaría de Movilidad de Cali",
				LocalDate.of(2024, 3, 1), LocalDate.of(2034, 3, 1), "C1");

		// Act
		DocumentView view = this.service.register(command);

		// Assert
		assertThat(view.licenseCategory()).isEqualTo("C1");
		assertThat(view.ownerType()).isEqualTo(OwnerType.DRIVER);
	}

	@Test
	void should_store_audit_and_events_without_correlation_when_there_is_none() {
		// Arrange
		givenTheCatalogHas(SOAT_RULES);
		givenIds(DOCUMENT_ID, EVENT_ID);
		givenTheClock();
		given(this.owners.isActiveOwner(VEHICLE)).willReturn(true);
		given(this.correlationIds.currentCorrelationId()).willReturn(Optional.empty());

		// Act
		this.service.register(soatCommand("AT-1", LocalDate.of(2027, 9, 30)));

		// Assert
		verify(this.auditLog).recordRegistration(any(Document.class), isNull());
		verify(this.outbox).append(eq(EVENT_ID), any(DocumentRegistered.class), isNull());
	}

	@Test
	void should_treat_a_blank_license_category_as_absent() {
		// Arrange
		givenTheCatalogHas(SOAT_RULES);
		givenIds(DOCUMENT_ID, EVENT_ID);
		givenTheClock();
		given(this.owners.isActiveOwner(VEHICLE)).willReturn(true);
		given(this.correlationIds.currentCorrelationId()).willReturn(Optional.empty());
		RegisterDocumentCommand command = new RegisterDocumentCommand(DocumentTypeCode.SOAT, OwnerType.VEHICLE,
				VEHICLE_ID, "AT-1", "Seguros Demo S.A.", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30), "  ");

		// Act
		DocumentView view = this.service.register(command);

		// Assert
		assertThat(view.licenseCategory()).isNull();
	}

	@Test
	void should_reject_a_type_missing_from_the_catalog() {
		// Arrange
		given(this.documentTypes.findByCode(DocumentTypeCode.SOAT)).willReturn(Optional.empty());

		// Act + Assert
		assertThatExceptionOfType(BusinessRuleViolationException.class)
			.isThrownBy(() -> this.service.register(soatCommand("AT-1", LocalDate.of(2027, 9, 30))))
			.withMessage("El tipo de documento SOAT no existe en el catálogo");
		verifyNoInteractions(this.documents, this.auditLog, this.outbox);
	}

	@Test
	void should_reject_an_owner_that_is_not_active_in_the_replica() {
		// Arrange
		givenTheCatalogHas(SOAT_RULES);
		givenIds(DOCUMENT_ID);
		givenTheClock();
		given(this.owners.isActiveOwner(VEHICLE)).willReturn(false);

		// Act + Assert
		assertThatExceptionOfType(BusinessRuleViolationException.class)
			.isThrownBy(() -> this.service.register(soatCommand("AT-1", LocalDate.of(2027, 9, 30))))
			.withMessage("El propietario VEHICLE " + VEHICLE_ID + " no existe o no está activo en la réplica local");
		verifyNoInteractions(this.documents, this.auditLog, this.outbox);
	}

	@Test
	void should_reject_a_number_already_registered_for_the_type() {
		// Arrange
		givenTheCatalogHas(SOAT_RULES);
		givenIds(DOCUMENT_ID);
		givenTheClock();
		given(this.owners.isActiveOwner(VEHICLE)).willReturn(true);
		given(this.documents.existsByTypeAndNumber(eq(DocumentTypeCode.SOAT), any())).willReturn(true);

		// Act + Assert
		assertThatExceptionOfType(DomainConflictException.class)
			.isThrownBy(() -> this.service.register(soatCommand("AT-1", LocalDate.of(2027, 9, 30))))
			.withMessage("Ya existe un documento SOAT con ese document_number");
		verify(this.documents, never()).save(any());
		verifyNoInteractions(this.auditLog, this.outbox);
	}

	@Test
	void should_reject_a_second_active_document_of_the_same_type_for_the_owner() {
		// Arrange
		givenTheCatalogHas(SOAT_RULES);
		givenIds(DOCUMENT_ID);
		givenTheClock();
		given(this.owners.isActiveOwner(VEHICLE)).willReturn(true);
		given(this.documents.existsByTypeAndNumber(eq(DocumentTypeCode.SOAT), any())).willReturn(false);
		given(this.documents.existsActiveByOwnerAndType(VEHICLE, DocumentTypeCode.SOAT)).willReturn(true);

		// Act + Assert
		assertThatExceptionOfType(DomainConflictException.class)
			.isThrownBy(() -> this.service.register(soatCommand("AT-1", LocalDate.of(2027, 9, 30))))
			.withMessage("El propietario ya tiene un documento SOAT activo. Para cambiarlo use la renovación");
		verify(this.documents, never()).save(any());
		verifyNoInteractions(this.auditLog, this.outbox);
	}

	@Test
	void should_reject_a_missing_command() {
		assertThatNullPointerException().isThrownBy(() -> this.service.register(null)).withMessage("command");
	}

	private void givenTheCatalogHas(DocumentTypeRules rules) {
		given(this.documentTypes.findByCode(rules.code())).willReturn(Optional.of(rules));
	}

	private void givenIds(UUID first, UUID... next) {
		given(this.idGenerator.newId()).willReturn(first, next);
	}

	private void givenTheClock() {
		given(this.clock.today()).willReturn(TODAY);
		given(this.clock.now()).willReturn(NOW);
	}

	private static RegisterDocumentCommand soatCommand(String number, LocalDate expirationDate) {
		return new RegisterDocumentCommand(DocumentTypeCode.SOAT, OwnerType.VEHICLE, VEHICLE_ID, number,
				"Seguros Demo S.A.", LocalDate.of(2026, 10, 1), expirationDate, null);
	}

}
