/**
 * Eventos de dominio.
 *
 * <p><b>Rol:</b> hechos de negocio que el núcleo registra en el outbox mediante un puerto de salida
 * (ADR-DOC-05): {@code DocumentRegistered}, {@code DocumentExpiringSoon}, {@code DocumentExpired} y
 * {@code DocumentRenewed} (DAS sec. 8.1).
 *
 * <p><b>Restricciones:</b> los eventos no incluyen {@code document_number} (DAS sec. 4.2). Mientras el
 * broker esté por definir (ADR-DOC-12), los eventos se guardan en el outbox pero no se publican.
 */
package co.edu.univalle.fleetops.document.service.domain.event;
