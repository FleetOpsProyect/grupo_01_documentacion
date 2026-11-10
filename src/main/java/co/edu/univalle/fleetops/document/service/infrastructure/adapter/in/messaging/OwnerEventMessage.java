package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging;

import java.util.Objects;

import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaEvent;

/**
 * Mensaje SQS ya traducido: el evento para el caso de uso y el correlation_id del sobre, que se pone en el MDC
 * para que los logs del procesamiento queden enlazados con la operación que originó el evento (DAS sec. 10).
 *
 * @param event evento de réplica
 * @param correlationId correlation_id del sobre, o {@code null} si no llegó
 */
public record OwnerEventMessage(OwnerReplicaEvent event, String correlationId) {

	public OwnerEventMessage {
		Objects.requireNonNull(event, "event");
	}

}
