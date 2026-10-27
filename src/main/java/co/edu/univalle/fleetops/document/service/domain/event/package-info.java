/**
 * Eventos de dominio.
 *
 * <p><b>Rol:</b> hechos de negocio que el núcleo registra en el outbox mediante un puerto de salida
 * (ADR-DOC-05): {@code DocumentRegistered}, {@code DocumentExpiringSoon}, {@code DocumentExpired} y
 * {@code DocumentRenewed} (DAS sec. 8.1), publicados en AWS SNS con los Message Attribute
 * {@code event_type} {@code document_registered_event}, {@code document_expiring_soon_event},
 * {@code document_expired_event} y {@code document_renewed_event} (ADR-DOC-14).
 * {@code DocumentRegistered} se emite siempre que el registro tiene éxito, no solo cuando nace vencido.
 *
 * <p><b>Restricciones:</b> los eventos no incluyen {@code document_number} (DAS sec. 4.2). El tópico
 * ({@code fleetops_documents_events}) todavía no está provisionado: mientras tanto, los eventos se
 * guardan en el outbox pero no se publican.
 */
package co.edu.univalle.fleetops.document.service.domain.event;
