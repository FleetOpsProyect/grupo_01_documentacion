package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionTemplate;

import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase;

/**
 * Decorator: evalúa cada documento en su propia transacción. El estado, la auditoría, el evento del outbox y
 * issued_alerts se confirman juntos o no se confirma ninguno (HU-DOC-06, criterio 6), y un error en un documento no
 * deshace los demás (criterio 7). Mantiene el servicio de aplicación libre de Spring.
 */
public final class TransactionalEvaluateDocumentExpirationUseCase implements EvaluateDocumentExpirationUseCase {

	private final EvaluateDocumentExpirationUseCase delegate;

	private final TransactionTemplate transactionTemplate;

	public TransactionalEvaluateDocumentExpirationUseCase(EvaluateDocumentExpirationUseCase delegate,
			TransactionTemplate transactionTemplate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
	}

	@Override
	public DocumentExpirationOutcome evaluate(UUID documentId, LocalDate today, UUID runId) {
		return this.transactionTemplate.execute(status -> this.delegate.evaluate(documentId, today, runId));
	}

}
