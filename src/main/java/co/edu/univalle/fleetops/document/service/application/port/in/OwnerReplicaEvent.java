package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;

/**
 * Evento de Vehículos o Conductores ya traducido por el adaptador de mensajería (Anti-Corruption Layer) al
 * lenguaje de este servicio (HU-DOC-10).
 *
 * @param eventId event_id del sobre; clave de idempotencia (processed_events)
 * @param eventType event_type del sobre, por ejemplo {@code driver_activated_event}
 * @param source slug del microservicio que lo publicó, por ejemplo {@code driver}
 * @param occurredAt instante en que ocurrió; se compara con last_event_at
 * @param owner vehículo o conductor al que se refiere
 * @param action alta o baja en la réplica
 * @param label placa del vehículo o nombre del conductor; obligatorio en un alta y {@code null} en una baja
 */
public record OwnerReplicaEvent(UUID eventId, String eventType, String source, Instant occurredAt, OwnerRef owner,
		OwnerReplicaAction action, String label) {

	public OwnerReplicaEvent {
		Objects.requireNonNull(eventId, "eventId");
		Objects.requireNonNull(eventType, "eventType");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(occurredAt, "occurredAt");
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(action, "action");
		if (action == OwnerReplicaAction.ACTIVATE) {
			Objects.requireNonNull(label, "label");
		}
	}

}
