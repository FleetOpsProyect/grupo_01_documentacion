package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.util.Objects;

import org.springframework.transaction.support.TransactionTemplate;

import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase;

/**
 * Decorator: publica cada lote en una transacción. Las filas quedan bloqueadas (FOR UPDATE SKIP LOCKED) mientras se
 * publican y sus marcas se confirman juntas al final del lote. Mantiene el servicio de aplicación libre de Spring.
 */
public final class TransactionalPublishPendingEventsUseCase implements PublishPendingEventsUseCase {

	private final PublishPendingEventsUseCase delegate;

	private final TransactionTemplate transactionTemplate;

	public TransactionalPublishPendingEventsUseCase(PublishPendingEventsUseCase delegate,
			TransactionTemplate transactionTemplate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
	}

	@Override
	public PublicationResult publishPending() {
		return this.transactionTemplate.execute(status -> this.delegate.publishPending());
	}

}
