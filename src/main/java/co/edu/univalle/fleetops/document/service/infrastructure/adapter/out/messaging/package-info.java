/**
 * Adaptador de salida de mensajería: publicador del outbox (ADR-DOC-05).
 *
 * <p><b>Estado:</b> vacío a propósito. El broker es AWS SNS + SQS (ADR-DOC-12, ADR-DOC-14), pero el
 * tópico todavía no está provisionado. Mientras tanto, los eventos se guardan en {@code outbox_events} y
 * no se publican.
 *
 * <p><b>Contenido previsto:</b> proceso que lee {@code outbox_events} tras el commit y publica en el
 * tópico SNS {@code fleetops_documents_events}, con el Message Attribute {@code event_type} (String) y el
 * sobre de dominio como Message, en raw message delivery para los suscriptores SQS. Sin
 * {@code message_key} como clave de partición: es un tópico SNS estándar, no FIFO (ver riesgo de orden,
 * DAS sec. 11). Cambiar de broker es cambiar solo este adaptador.
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.messaging;
