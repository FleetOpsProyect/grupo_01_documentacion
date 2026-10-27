package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.util.Objects;

import org.springframework.transaction.support.TransactionTemplate;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentCommand;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentUseCase;

/**
 * Decorator: ejecuta el registro en una sola transacción. El documento, la auditoría y los eventos del outbox se
 * confirman juntos o no se confirma ninguno (ADR-DOC-05). Mantiene el servicio de aplicación libre de Spring.
 */
public final class TransactionalRegisterDocumentUseCase implements RegisterDocumentUseCase {

	private final RegisterDocumentUseCase delegate;

	private final TransactionTemplate transactionTemplate;

	public TransactionalRegisterDocumentUseCase(RegisterDocumentUseCase delegate,
			TransactionTemplate transactionTemplate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
	}

	@Override
	public DocumentView register(RegisterDocumentCommand command) {
		return this.transactionTemplate.execute(status -> this.delegate.register(command));
	}

}
