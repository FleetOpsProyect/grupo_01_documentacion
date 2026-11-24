package co.edu.univalle.fleetops.document.service.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase.DocumentExpirationOutcome;
import co.edu.univalle.fleetops.document.service.application.port.out.AuditLogRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository.ExpirationCandidate;
import co.edu.univalle.fleetops.document.service.application.port.out.IssuedAlertRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpiringSoon;
import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.service.AlertThresholdPolicy;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EvaluateDocumentExpirationServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

	private static final Instant NOW = Instant.parse("2026-11-23T06:00:00Z");

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID RUN_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000a1");

	private static final OwnerRef VEHICLE = new OwnerRef(OwnerType.VEHICLE,
			UUID.fromString("01960000-0000-7000-8000-000000000001"));

	@Mock
	private DocumentRepository documents;

	@Mock
	private IssuedAlertRepository issuedAlerts;

	@Mock
	private AuditLogRepository auditLog;

	@Mock
	private OutboxRepository outbox;

	@Mock
	private BusinessClock clock;

	private EvaluateDocumentExpirationService service() {
		return new EvaluateDocumentExpirationService(this.documents, this.issuedAlerts, this.auditLog, this.outbox,
				this.clock, new DocumentStatusCalculator(30), new AlertThresholdPolicy(List.of(30, 15, 7, 1)));
	}

	private void givenCandidate(int daysRemaining, DocumentStatus status) {
		given(this.documents.lockExpirationCandidate(DOCUMENT_ID)).willReturn(Optional.of(new ExpirationCandidate(
				DOCUMENT_ID, DocumentTypeCode.SOAT, VEHICLE, TODAY.plusDays(daysRemaining), status)));
	}

	@Test
	void should_mark_expiring_soon_and_publish_the_first_alert_in_one_go() {
		// Arrange: VALID a 30 días, ningún umbral emitido
		givenCandidate(30, DocumentStatus.VALID);
		given(this.clock.now()).willReturn(NOW);
		given(this.issuedAlerts.issuedThresholds(DOCUMENT_ID)).willReturn(Set.of());

		// Act
		DocumentExpirationOutcome outcome = service().evaluate(DOCUMENT_ID, TODAY, RUN_ID);

		// Assert: estado, auditoría, evento y umbral (criterio 6)
		assertThat(outcome).isEqualTo(DocumentExpirationOutcome.evaluated(true, true));
		UUID eventId = DocumentExpiringSoon.eventIdFor(DOCUMENT_ID, 30);
		InOrder order = inOrder(this.documents, this.auditLog, this.outbox, this.issuedAlerts);
		order.verify(this.documents).updateStatus(DOCUMENT_ID, DocumentStatus.EXPIRING_SOON, NOW);
		order.verify(this.auditLog)
			.recordStatusChange(DOCUMENT_ID, DocumentStatus.VALID, DocumentStatus.EXPIRING_SOON, RUN_ID, NOW);
		ArgumentCaptor<DomainEvent> event = ArgumentCaptor.forClass(DomainEvent.class);
		order.verify(this.outbox).append(eq(eventId), event.capture(), eq(RUN_ID));
		order.verify(this.issuedAlerts).record(DOCUMENT_ID, 30, eventId, NOW);
		assertThat(event.getValue()).isEqualTo(new DocumentExpiringSoon(DOCUMENT_ID, DocumentTypeCode.SOAT, VEHICLE,
				TODAY.plusDays(30), 30, 30, NOW));
	}

	@Test
	void should_publish_the_smallest_pending_threshold_and_record_the_larger_ones_without_event() {
		// Arrange (criterio 5, ADR-DOC-03): ya EXPIRING_SOON, a 5 días y solo se había emitido el de 30
		givenCandidate(5, DocumentStatus.EXPIRING_SOON);
		given(this.clock.now()).willReturn(NOW);
		given(this.issuedAlerts.issuedThresholds(DOCUMENT_ID)).willReturn(Set.of(30));

		// Act
		DocumentExpirationOutcome outcome = service().evaluate(DOCUMENT_ID, TODAY, RUN_ID);

		// Assert
		assertThat(outcome).isEqualTo(DocumentExpirationOutcome.evaluated(true, false));
		UUID eventId = DocumentExpiringSoon.eventIdFor(DOCUMENT_ID, 7);
		verify(this.outbox).append(eq(eventId), any(DocumentExpiringSoon.class), eq(RUN_ID));
		verify(this.issuedAlerts).record(DOCUMENT_ID, 7, eventId, NOW);
		verify(this.issuedAlerts).record(DOCUMENT_ID, 15, null, NOW);
		verify(this.documents, never()).updateStatus(any(), any(), any());
		verifyNoInteractions(this.auditLog);
	}

	@Test
	void should_change_nothing_when_the_threshold_was_already_issued() {
		// Arrange (criterio 4)
		givenCandidate(20, DocumentStatus.EXPIRING_SOON);
		given(this.clock.now()).willReturn(NOW);
		given(this.issuedAlerts.issuedThresholds(DOCUMENT_ID)).willReturn(Set.of(30));

		// Act
		DocumentExpirationOutcome outcome = service().evaluate(DOCUMENT_ID, TODAY, RUN_ID);

		// Assert
		assertThat(outcome).isEqualTo(DocumentExpirationOutcome.evaluated(false, false));
		verifyNoInteractions(this.outbox, this.auditLog);
		verify(this.issuedAlerts, never()).record(any(), anyInt(), any(), any());
	}

	@Test
	void should_return_to_valid_when_the_largest_threshold_was_lowered() {
		// Arrange: EXPIRING_SOON a 40 días (antes el umbral mayor era 45)
		givenCandidate(40, DocumentStatus.EXPIRING_SOON);
		given(this.clock.now()).willReturn(NOW);
		given(this.issuedAlerts.issuedThresholds(DOCUMENT_ID)).willReturn(Set.of());

		// Act
		DocumentExpirationOutcome outcome = service().evaluate(DOCUMENT_ID, TODAY, RUN_ID);

		// Assert: la regla única manda (ADR-DOC-01) y no hay alerta
		assertThat(outcome).isEqualTo(DocumentExpirationOutcome.evaluated(false, true));
		verify(this.documents).updateStatus(DOCUMENT_ID, DocumentStatus.VALID, NOW);
		verify(this.auditLog)
			.recordStatusChange(DOCUMENT_ID, DocumentStatus.EXPIRING_SOON, DocumentStatus.VALID, RUN_ID, NOW);
		verifyNoInteractions(this.outbox);
	}

	@Test
	void should_skip_a_document_that_is_no_longer_a_candidate() {
		// Arrange: se renovó o se anuló entre la consulta y la evaluación
		given(this.documents.lockExpirationCandidate(DOCUMENT_ID)).willReturn(Optional.empty());

		// Act
		DocumentExpirationOutcome outcome = service().evaluate(DOCUMENT_ID, TODAY, RUN_ID);

		// Assert
		assertThat(outcome).isEqualTo(DocumentExpirationOutcome.skippedOutcome());
		verifyNoInteractions(this.issuedAlerts, this.auditLog, this.outbox, this.clock);
	}

	@Test
	void should_leave_an_expired_document_to_hu_doc_07() {
		// Arrange
		givenCandidate(-1, DocumentStatus.EXPIRING_SOON);

		// Act
		DocumentExpirationOutcome outcome = service().evaluate(DOCUMENT_ID, TODAY, RUN_ID);

		// Assert
		assertThat(outcome.skipped()).isTrue();
		verify(this.documents, never()).updateStatus(any(), any(), any());
		verifyNoInteractions(this.issuedAlerts, this.outbox);
	}

	@Test
	void should_record_silent_thresholds_without_an_event() {
		// Arrange: a 6 días y ya se avisó el de 7; se agregó el umbral 15 a la configuración
		givenCandidate(6, DocumentStatus.EXPIRING_SOON);
		given(this.clock.now()).willReturn(NOW);
		given(this.issuedAlerts.issuedThresholds(DOCUMENT_ID)).willReturn(Set.of(30, 7));

		// Act
		DocumentExpirationOutcome outcome = service().evaluate(DOCUMENT_ID, TODAY, RUN_ID);

		// Assert
		assertThat(outcome.alertPublished()).isFalse();
		verify(this.issuedAlerts).record(eq(DOCUMENT_ID), eq(15), isNull(), eq(NOW));
		verifyNoInteractions(this.outbox);
	}

	@Test
	void should_reject_missing_dependencies_and_arguments() {
		DocumentStatusCalculator calculator = new DocumentStatusCalculator(30);
		AlertThresholdPolicy policy = new AlertThresholdPolicy(List.of(30));
		assertThatNullPointerException().isThrownBy(() -> new EvaluateDocumentExpirationService(null,
				this.issuedAlerts, this.auditLog, this.outbox, this.clock, calculator, policy))
			.withMessage("documents");
		assertThatNullPointerException().isThrownBy(() -> new EvaluateDocumentExpirationService(this.documents,
				null, this.auditLog, this.outbox, this.clock, calculator, policy)).withMessage("issuedAlerts");
		assertThatNullPointerException().isThrownBy(() -> new EvaluateDocumentExpirationService(this.documents,
				this.issuedAlerts, null, this.outbox, this.clock, calculator, policy)).withMessage("auditLog");
		assertThatNullPointerException().isThrownBy(() -> new EvaluateDocumentExpirationService(this.documents,
				this.issuedAlerts, this.auditLog, null, this.clock, calculator, policy)).withMessage("outbox");
		assertThatNullPointerException().isThrownBy(() -> new EvaluateDocumentExpirationService(this.documents,
				this.issuedAlerts, this.auditLog, this.outbox, null, calculator, policy)).withMessage("clock");
		assertThatNullPointerException().isThrownBy(() -> new EvaluateDocumentExpirationService(this.documents,
				this.issuedAlerts, this.auditLog, this.outbox, this.clock, null, policy))
			.withMessage("statusCalculator");
		assertThatNullPointerException().isThrownBy(() -> new EvaluateDocumentExpirationService(this.documents,
				this.issuedAlerts, this.auditLog, this.outbox, this.clock, calculator, null))
			.withMessage("alertPolicy");
		assertThatNullPointerException().isThrownBy(() -> service().evaluate(null, TODAY, RUN_ID))
			.withMessage("documentId");
		assertThatNullPointerException().isThrownBy(() -> service().evaluate(DOCUMENT_ID, null, RUN_ID))
			.withMessage("today");
		assertThatNullPointerException().isThrownBy(() -> service().evaluate(DOCUMENT_ID, TODAY, null))
			.withMessage("runId");
	}

}
