package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

/**
 * Aviso de Spring: la transacción en curso guardó eventos en el outbox. El relevo del outbox lo escucha después del
 * commit para publicar sin esperar al siguiente ciclo de consulta. Si la transacción se revierte, el aviso no llega.
 */
public record OutboxEventsAppended() {

}
