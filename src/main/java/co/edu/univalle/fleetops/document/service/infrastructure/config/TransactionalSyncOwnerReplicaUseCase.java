package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.util.Objects;

import org.springframework.transaction.support.TransactionTemplate;

import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaEvent;
import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaSyncOutcome;
import co.edu.univalle.fleetops.document.service.application.port.in.SyncOwnerReplicaUseCase;

/**
 * Decorator: aplica el evento en una sola transacción. La réplica y el event_id en processed_events se confirman
 * juntos o no se confirma ninguno (HU-DOC-10, criterio 3). Mantiene el servicio de aplicación libre de Spring.
 */
public final class TransactionalSyncOwnerReplicaUseCase implements SyncOwnerReplicaUseCase {

	private final SyncOwnerReplicaUseCase delegate;

	private final TransactionTemplate transactionTemplate;

	public TransactionalSyncOwnerReplicaUseCase(SyncOwnerReplicaUseCase delegate,
			TransactionTemplate transactionTemplate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
	}

	@Override
	public OwnerReplicaSyncOutcome sync(OwnerReplicaEvent event) {
		return this.transactionTemplate.execute(status -> this.delegate.sync(event));
	}

}
