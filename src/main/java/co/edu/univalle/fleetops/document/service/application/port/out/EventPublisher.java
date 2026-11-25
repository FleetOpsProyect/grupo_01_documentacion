package co.edu.univalle.fleetops.document.service.application.port.out;

import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository.PendingEvent;

/**
 * Puerto de salida: envía un evento del outbox al broker (AWS SNS, tópico fleetops_document_events; ADR-DOC-12 y
 * ADR-DOC-14). Cambiar de broker es cambiar solo su adaptador.
 */
public interface EventPublisher {

	/**
	 * Publica el evento. Si vuelve sin excepción, el broker lo aceptó.
	 *
	 * @throws EventPublicationException si no se pudo publicar; indica si el error es transitorio o propio del evento
	 */
	void publish(PendingEvent event);

}
