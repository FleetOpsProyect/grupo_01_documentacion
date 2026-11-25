/**
 * Adaptador de salida de mensajería: publicación de los eventos del outbox en AWS SNS (ADR-DOC-05, ADR-DOC-12,
 * ADR-DOC-14).
 *
 * <p>{@link co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.messaging.SnsEventPublisher}
 * publica en el tópico estándar {@code fleetops_document_events} (ARN en {@code MESSAGING_DOCUMENT_TOPIC_ARN}) el
 * sobre de dominio como Message y el Message Attribute {@code event_type} (String). Los suscriptores usan raw message
 * delivery y FilterPolicy sobre ese atributo. Sin {@code message_key} como clave de partición: es un tópico SNS
 * estándar, no FIFO (ver riesgo de orden, DAS sec. 11). Cambiar de broker es cambiar solo este adaptador.
 *
 * <p>Lo invoca {@code PublishPendingEventsService}, que dispara el relevo del outbox; solo existe con
 * {@code fleetops.messaging.enabled=true} (perfiles {@code local} y {@code aws}).
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.messaging;
