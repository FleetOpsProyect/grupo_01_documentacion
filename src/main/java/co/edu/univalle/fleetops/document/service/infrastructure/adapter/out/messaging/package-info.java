/**
 * Adaptador de salida de mensajería: publicador del outbox (ADR-DOC-05).
 *
 * <p><b>Estado:</b> vacío a propósito. El broker (Apache Kafka o AWS SNS + SQS) está por definir
 * (ADR-DOC-12). Mientras tanto, los eventos se guardan en {@code outbox_events} y no se publican.
 *
 * <p><b>Contenido previsto:</b> proceso que lee {@code outbox_events} tras el commit y publica en
 * {@code fleetops.documents.document.v1} con clave {@code owner_id}. Cambiar de broker es cambiar solo este
 * adaptador.
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.messaging;
