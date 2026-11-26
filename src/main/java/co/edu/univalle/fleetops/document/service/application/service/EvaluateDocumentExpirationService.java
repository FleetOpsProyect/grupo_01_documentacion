package co.edu.univalle.fleetops.document.service.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase;
import co.edu.univalle.fleetops.document.service.application.port.out.AuditLogRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository.ExpirationCandidate;
import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.application.port.out.IssuedAlertRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpiringSoon;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.service.AlertDecision;
import co.edu.univalle.fleetops.document.service.domain.service.AlertThresholdPolicy;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;
import co.edu.univalle.fleetops.document.service.domain.service.StatusEvaluation;

/**
 * Application Service: evalúa un documento en la verificación diaria de vigencia (HU-DOC-06).
 *
 * <p>Orden: bloquear el documento y confirmar que sigue siendo candidato; calcular el estado con la regla única y
 * guardarlo si cambió, con su auditoría (criterio 6, ADR-DOC-01); decidir los umbrales con
 * {@link AlertThresholdPolicy} (ADR-DOC-03); guardar el evento en el outbox y después los umbrales en issued_alerts.
 * La transacción la aplica un decorador de infraestructura, para que esta clase no dependa de Spring.
 *
 * <p>Un documento que ya venció no se toca aquí: su paso a EXPIRED y document_expired_event son de HU-DOC-07.
 */
public final class EvaluateDocumentExpirationService implements EvaluateDocumentExpirationUseCase {

	private final DocumentRepository documents;

	private final IssuedAlertRepository issuedAlerts;

	private final AuditLogRepository auditLog;

	private final OutboxRepository outbox;

	private final BusinessClock clock;

	private final DocumentStatusCalculator statusCalculator;

	private final AlertThresholdPolicy alertPolicy;

	private final IdGenerator idGenerator;

	public EvaluateDocumentExpirationService(DocumentRepository documents, IssuedAlertRepository issuedAlerts,
			AuditLogRepository auditLog, OutboxRepository outbox, BusinessClock clock,
			DocumentStatusCalculator statusCalculator, AlertThresholdPolicy alertPolicy, IdGenerator idGenerator) {
		this.documents = Objects.requireNonNull(documents, "documents");
		this.issuedAlerts = Objects.requireNonNull(issuedAlerts, "issuedAlerts");
		this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
		this.outbox = Objects.requireNonNull(outbox, "outbox");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.statusCalculator = Objects.requireNonNull(statusCalculator, "statusCalculator");
		this.alertPolicy = Objects.requireNonNull(alertPolicy, "alertPolicy");
		this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
	}

	@Override
	public DocumentExpirationOutcome evaluate(UUID documentId, LocalDate today, UUID runId) {
		Objects.requireNonNull(documentId, "documentId");
		Objects.requireNonNull(today, "today");
		Objects.requireNonNull(runId, "runId");
		Optional<ExpirationCandidate> locked = this.documents.lockExpirationCandidate(documentId);
		if (locked.isEmpty()) {
			return DocumentExpirationOutcome.skippedOutcome();
		}
		ExpirationCandidate candidate = locked.get();
		StatusEvaluation evaluation = this.statusCalculator.evaluate(candidate.expirationDate(), today);
		if (evaluation.status() == DocumentStatus.EXPIRED) {
			return DocumentExpirationOutcome.skippedOutcome();
		}
		Instant now = this.clock.now();
		boolean statusChanged = persistStatus(candidate, evaluation.status(), runId, now);
		boolean alertPublished = registerAlerts(candidate, evaluation.daysRemaining(), runId, now);
		return DocumentExpirationOutcome.evaluated(alertPublished, statusChanged);
	}

	private boolean persistStatus(ExpirationCandidate candidate, DocumentStatus status, UUID runId, Instant now) {
		if (status == candidate.status()) {
			return false;
		}
		this.documents.updateStatus(candidate.documentId(), status, now);
		this.auditLog.recordStatusChange(candidate.documentId(), candidate.status(), status, runId, now);
		return true;
	}

	private boolean registerAlerts(ExpirationCandidate candidate, int daysRemaining, UUID runId, Instant now) {
		AlertDecision decision = this.alertPolicy.decide(daysRemaining,
				this.issuedAlerts.issuedThresholds(candidate.documentId()));
		OptionalInt published = decision.publishedThreshold();
		if (published.isPresent()) {
			int threshold = published.getAsInt();
			// event_id no determinista, como el de los demás eventos; los duplicados los impide issued_alerts
			UUID eventId = this.idGenerator.newId();
			// El evento va primero: issued_alerts.outbox_event_id lo referencia
			this.outbox.append(eventId, new DocumentExpiringSoon(candidate.documentId(), candidate.documentType(),
					candidate.owner(), candidate.expirationDate(), threshold, daysRemaining, now), runId);
			this.issuedAlerts.record(candidate.documentId(), threshold, eventId, now);
		}
		for (int threshold : decision.silentThresholds()) {
			this.issuedAlerts.record(candidate.documentId(), threshold, null, now);
		}
		return published.isPresent();
	}

}
