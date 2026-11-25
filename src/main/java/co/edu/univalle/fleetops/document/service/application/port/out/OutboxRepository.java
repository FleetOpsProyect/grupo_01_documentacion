package co.edu.univalle.fleetops.document.service.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;

/**
 * Puerto de salida: Transactional Outbox (ADR-DOC-05). El evento se guarda en la misma transacción que el cambio;
 * el publicador lo envía a AWS SNS después del commit y lo marca como publicado.
 */
public interface OutboxRepository {

	/**
	 * @param eventId event_id del sobre
	 * @param event evento de dominio
	 * @param correlationId correlación de la petición; vacío fuera de una petición HTTP
	 */
	void append(UUID eventId, DomainEvent event, UUID correlationId);

	/**
	 * Toma hasta {@code limit} eventos PENDING, del más antiguo al más reciente, y los bloquea hasta el fin de la
	 * transacción. Los que ya bloqueó otra instancia se saltan, así que dos publicadores nunca envían la misma fila a
	 * la vez.
	 */
	List<PendingEvent> lockPending(int limit);

	/**
	 * Marca el evento como PUBLISHED, cuenta el intento y guarda {@code published_at}.
	 */
	void markPublished(UUID eventId, Instant publishedAt);

	/**
	 * Cuenta un intento fallido y guarda el motivo.
	 *
	 * @param permanent {@code true} pasa el evento a FAILED: no se reintenta; {@code false} lo deja en PENDING para
	 * el próximo ciclo
	 */
	void recordFailure(UUID eventId, String error, boolean permanent);

	/**
	 * Evento listo para publicar.
	 *
	 * @param eventId event_id del sobre
	 * @param eventType valor del Message Attribute event_type
	 * @param topic nombre del tópico SNS de destino
	 * @param payload sobre completo en JSON
	 * @param correlationId correlation_id del sobre; vacío si no lo tiene
	 */
	record PendingEvent(UUID eventId, String eventType, String topic, String payload, UUID correlationId) {

		public PendingEvent {
			Objects.requireNonNull(eventId, "eventId");
			Objects.requireNonNull(eventType, "eventType");
			Objects.requireNonNull(topic, "topic");
			Objects.requireNonNull(payload, "payload");
		}

	}

}
