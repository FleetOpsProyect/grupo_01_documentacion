package co.edu.univalle.fleetops.document.service.application.service;

import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.application.port.in.CancelDocumentCommand;
import co.edu.univalle.fleetops.document.service.application.port.in.CancelDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.out.AuditLogRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.CorrelationIdProvider;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.domain.exception.ResourceNotFoundException;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.service.StatusEvaluation;

/**
 * Servicio de aplicación para anular documentos (HU-DOC-04).
 *
 * <p>Bloquea el documento, aplica las reglas del dominio, persiste la
 * anulación y registra su auditoría. No publica eventos de dominio.
 * La transacción se aplica mediante un decorador de infraestructura.
 */
public final class CancelDocumentService implements CancelDocumentUseCase {

	private final DocumentRepository documents;

	private final AuditLogRepository auditLog;

	private final CorrelationIdProvider correlationIds;

	private final BusinessClock clock;

	public CancelDocumentService(DocumentRepository documents,
								 AuditLogRepository auditLog, CorrelationIdProvider correlationIds,
								 BusinessClock clock) {

		this.documents = Objects.requireNonNull(documents, "documents");
		this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
		this.correlationIds = Objects.requireNonNull(correlationIds, "correlationIds");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public DocumentView cancel(CancelDocumentCommand command) {
		Objects.requireNonNull(command, "command");

		Document previous = this.documents.findByIdForUpdate(command.documentId())
				.orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));

		Document cancelled = previous.cancel(command.reason(), this.clock.now());

		this.documents.updateCancellation(cancelled);

		UUID correlationId = this.correlationIds.currentCorrelationId().orElse(null);

		this.auditLog.recordCancellation(previous, cancelled, correlationId);

		return DocumentView.of(cancelled,
				new StatusEvaluation(cancelled.status(), null));
	}

}
