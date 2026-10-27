package co.edu.univalle.fleetops.document.service.application.port.out;

import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;

/**
 * Puerto de salida: Transactional Outbox (ADR-DOC-05). El evento se guarda en la misma transacción que el cambio;
 * otro proceso lo publica después del commit. En esta versión los eventos solo se guardan (no hay publicador).
 */
public interface OutboxRepository {

	/**
	 * @param eventId event_id del sobre
	 * @param event evento de dominio
	 * @param correlationId correlación de la petición; vacío fuera de una petición HTTP
	 */
	void append(UUID eventId, DomainEvent event, UUID correlationId);

}
