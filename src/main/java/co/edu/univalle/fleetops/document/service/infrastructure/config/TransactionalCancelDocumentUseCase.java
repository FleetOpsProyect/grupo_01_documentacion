package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.util.Objects;

import org.springframework.transaction.support.TransactionTemplate;

import co.edu.univalle.fleetops.document.service.application.port.in.CancelDocumentCommand;
import co.edu.univalle.fleetops.document.service.application.port.in.CancelDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;

/**
 * Decorator: ejecuta la anulación en una sola transacción (HU-DOC-04).
 * La actualización del documento y su auditoría se confirman juntas
 * o no se confirma ninguna. Mantiene el servicio de aplicación libre de Spring.
 */
public final class TransactionalCancelDocumentUseCase implements CancelDocumentUseCase {

	private final CancelDocumentUseCase delegate;

	private final TransactionTemplate transactionTemplate;

	public TransactionalCancelDocumentUseCase(CancelDocumentUseCase delegate,
											  TransactionTemplate transactionTemplate) {

		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
	}

	@Override
	public DocumentView cancel(CancelDocumentCommand command) {
		return this.transactionTemplate.execute(status -> this.delegate.cancel(command));
	}

}
