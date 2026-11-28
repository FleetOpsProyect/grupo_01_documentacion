package co.edu.univalle.fleetops.document.service.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.exception.InvalidCancellationReasonException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.in.CancelDocumentCommand;
import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.out.AuditLogRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.CorrelationIdProvider;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException;
import co.edu.univalle.fleetops.document.service.domain.exception.ResourceNotFoundException;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentRegistration;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.model.ValidityPeriod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class CancelDocumentServiceTest {

	private static final UUID DOCUMENT_ID = UUID.randomUUID();

	private static final UUID OWNER_ID = UUID.randomUUID();

	private static final UUID CORRELATION_ID = UUID.randomUUID();

	private static final UUID PREVIOUS_DOCUMENT_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-10-26T15:00:00Z");

	private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");

	private static final String REASON = "Documento registrado por error";

	@Mock
	private DocumentRepository documents;

	@Mock
	private AuditLogRepository auditLog;

	@Mock
	private CorrelationIdProvider correlationIds;

	@Mock
	private BusinessClock clock;

	@InjectMocks
	private CancelDocumentService service;

	@ParameterizedTest
	@EnumSource(value = DocumentStatus.class,
			names = { "VALID", "EXPIRING_SOON", "EXPIRED" })
	void should_cancel_an_active_document_and_record_its_audit(DocumentStatus status) {
		// Arrange
		Document previous = documentWithStatus(status);

		given(this.documents.findByIdForUpdate(DOCUMENT_ID))
				.willReturn(Optional.of(previous));
		given(this.clock.now()).willReturn(NOW);
		given(this.correlationIds.currentCorrelationId())
				.willReturn(Optional.of(CORRELATION_ID));

		// Act
		DocumentView view = this.service.cancel(command());

		// Assert
		ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);

		InOrder order = inOrder(this.documents, this.clock,
				this.correlationIds, this.auditLog);

		order.verify(this.documents).findByIdForUpdate(DOCUMENT_ID);
		order.verify(this.clock).now();
		order.verify(this.documents).updateCancellation(captor.capture());

		Document cancelled = captor.getValue();

		order.verify(this.correlationIds).currentCorrelationId();
		order.verify(this.auditLog)
				.recordCancellation(previous, cancelled, CORRELATION_ID);

		assertThat(cancelled.id()).isEqualTo(DOCUMENT_ID);
		assertThat(cancelled.status()).isEqualTo(DocumentStatus.CANCELLED);
		assertThat(cancelled.cancellationReason()).isEqualTo(REASON);
		assertThat(cancelled.cancelledAt()).isEqualTo(NOW);
		assertThat(cancelled.pullDomainEvents()).isEmpty();

		assertThat(previous.status()).isEqualTo(status);
		assertThat(previous.cancellationReason()).isNull();
		assertThat(previous.cancelledAt()).isNull();

		assertThat(view).isEqualTo(new DocumentView(
				DOCUMENT_ID, DocumentTypeCode.SOAT, OwnerType.VEHICLE,
				OWNER_ID, "SOAT-12345", "Aseguradora Ejemplo",
				previous.validity().issueDate(),
				previous.validity().expirationDate(), null,
				DocumentStatus.CANCELLED, null, PREVIOUS_DOCUMENT_ID,
				REASON, NOW, CREATED, NOW));

		verifyNoMoreInteractions(this.documents, this.clock,
				this.correlationIds, this.auditLog);
	}

	@Test
	void should_record_cancellation_without_correlation_when_there_is_none() {
		// Arrange
		Document previous = documentWithStatus(DocumentStatus.VALID);

		given(this.documents.findByIdForUpdate(DOCUMENT_ID))
				.willReturn(Optional.of(previous));
		given(this.clock.now()).willReturn(NOW);
		given(this.correlationIds.currentCorrelationId())
				.willReturn(Optional.empty());

		// Act
		DocumentView view = this.service.cancel(command());

		// Assert
		assertThat(view.status()).isEqualTo(DocumentStatus.CANCELLED);

		verify(this.documents).updateCancellation(any(Document.class));
		verify(this.auditLog)
				.recordCancellation(eq(previous), any(Document.class), isNull());
	}

	@Test
	void should_throw_exception_when_document_is_not_found() {
		// Arrange
		given(this.documents.findByIdForUpdate(DOCUMENT_ID))
				.willReturn(Optional.empty());

		// Act and Assert
		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> this.service.cancel(command()))
				.withMessage("Documento no encontrado");

		verify(this.documents).findByIdForUpdate(DOCUMENT_ID);
		verifyNoMoreInteractions(this.documents);
		verifyNoInteractions(this.auditLog, this.clock, this.correlationIds);
	}

	@ParameterizedTest
	@EnumSource(value = DocumentStatus.class,
			names = { "REPLACED", "CANCELLED" })
	void should_reject_cancellation_of_a_terminal_document(DocumentStatus status) {
		// Arrange
		Document previous = documentWithStatus(status);

		given(this.documents.findByIdForUpdate(DOCUMENT_ID))
				.willReturn(Optional.of(previous));
		given(this.clock.now()).willReturn(NOW);

		// Act and Assert
		assertThatExceptionOfType(DomainConflictException.class)
				.isThrownBy(() -> this.service.cancel(command()))
				.withMessage("El documento no se puede modificar en su estado actual");

		verify(this.documents, never()).updateCancellation(any());
		verifyNoInteractions(this.auditLog, this.correlationIds);
	}

	@Test
	void should_reject_an_invalid_cancellation_reason() {
		// Arrange
		Document previous = documentWithStatus(DocumentStatus.VALID);

		given(this.documents.findByIdForUpdate(DOCUMENT_ID))
				.willReturn(Optional.of(previous));
		given(this.clock.now()).willReturn(NOW);

		CancelDocumentCommand invalid = new CancelDocumentCommand(DOCUMENT_ID, "Error");

		// Act and Assert
		assertThatExceptionOfType(InvalidCancellationReasonException.class)
				.isThrownBy(() -> this.service.cancel(invalid))
				.withMessage(
						"El motivo de anulación es obligatorio y debe tener entre 10 y 200 caracteres");

		verify(this.documents, never()).updateCancellation(any());
		verifyNoInteractions(this.auditLog, this.correlationIds);
	}

	@Test
	void should_not_record_audit_when_cancellation_update_fails() {
		// Arrange
		Document previous = documentWithStatus(DocumentStatus.VALID);

		given(this.documents.findByIdForUpdate(DOCUMENT_ID))
				.willReturn(Optional.of(previous));
		given(this.clock.now()).willReturn(NOW);

		willThrow(new IllegalStateException("Error de persistencia"))
				.given(this.documents).updateCancellation(any(Document.class));

		// Act and Assert
		assertThatIllegalStateException()
				.isThrownBy(() -> this.service.cancel(command()))
				.withMessage("Error de persistencia");

		verifyNoInteractions(this.auditLog, this.correlationIds);
	}

	@Test
	void should_reject_a_missing_command() {
		assertThatNullPointerException()
				.isThrownBy(() -> this.service.cancel(null))
				.withMessage("command");

		verifyNoInteractions(this.documents, this.auditLog,
				this.correlationIds, this.clock);
	}

	@Test
	void should_cancel_a_property_card_without_expiration_date() {
		// Arrange
		DocumentRegistration registration = new DocumentRegistration(
				DOCUMENT_ID,
				new OwnerRef(OwnerType.VEHICLE, OWNER_ID),
				new DocumentNumber("TP-12345"),
				"Entidad de tránsito",
				new ValidityPeriod(LocalDate.of(2026, 10, 26), null),
				null);

		Document previous = Document.reconstitute(
				registration,
				DocumentTypeCode.PROPERTY_CARD,
				DocumentStatus.VALID,
				CREATED,
				CREATED,
				null,
				null,
				null);

		given(this.documents.findByIdForUpdate(DOCUMENT_ID))
				.willReturn(Optional.of(previous));
		given(this.clock.now()).willReturn(NOW);
		given(this.correlationIds.currentCorrelationId())
				.willReturn(Optional.of(CORRELATION_ID));

		// Act
		DocumentView view = this.service.cancel(command());

		// Assert
		ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);

		verify(this.documents).updateCancellation(captor.capture());

		Document cancelled = captor.getValue();

		assertThat(cancelled.id()).isEqualTo(DOCUMENT_ID);
		assertThat(cancelled.documentType()).isEqualTo(DocumentTypeCode.PROPERTY_CARD);
		assertThat(cancelled.status()).isEqualTo(DocumentStatus.CANCELLED);
		assertThat(cancelled.validity().expirationDate()).isNull();
		assertThat(cancelled.cancellationReason()).isEqualTo(REASON);
		assertThat(cancelled.cancelledAt()).isEqualTo(NOW);
		assertThat(cancelled.pullDomainEvents()).isEmpty();

		assertThat(previous.status()).isEqualTo(DocumentStatus.VALID);

		assertThat(view.documentType()).isEqualTo(DocumentTypeCode.PROPERTY_CARD);
		assertThat(view.status()).isEqualTo(DocumentStatus.CANCELLED);
		assertThat(view.expirationDate()).isNull();
		assertThat(view.daysRemaining()).isNull();

		verify(this.auditLog)
				.recordCancellation(previous, cancelled, CORRELATION_ID);
	}

	private static CancelDocumentCommand command() {
		return new CancelDocumentCommand(DOCUMENT_ID, REASON);
	}

	private static Document documentWithStatus(DocumentStatus status) {
		LocalDate expirationDate = switch (status) {
			case EXPIRED -> LocalDate.of(2026, 11, 26);
			case EXPIRING_SOON -> LocalDate.of(2026, 12, 1);
			default -> LocalDate.of(2027, 10, 26);
		};

		DocumentRegistration registration = new DocumentRegistration(
				DOCUMENT_ID,
				new OwnerRef(OwnerType.VEHICLE, OWNER_ID),
				new DocumentNumber("SOAT-12345"),
				"Aseguradora Ejemplo",
				new ValidityPeriod(LocalDate.of(2026, 10, 26), expirationDate),
				null);

		return Document.reconstitute(
				registration,
				DocumentTypeCode.SOAT,
				status,
				CREATED,
				CREATED,
				PREVIOUS_DOCUMENT_ID,
				null,
				null);
	}

}
