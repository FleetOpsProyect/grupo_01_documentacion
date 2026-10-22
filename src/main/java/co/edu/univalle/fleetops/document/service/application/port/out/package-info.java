/**
 * Puertos de salida.
 *
 * <p><b>Rol:</b> interfaces que el núcleo necesita y que implementan los adaptadores de salida.
 *
 * <p><b>Patrones:</b> Repository (Hexagonal Port), Transactional Outbox (ADR-DOC-05) y Clock como puerto.
 *
 * <p><b>Contenido previsto:</b> {@code DocumentRepository}, {@code DocumentTypeRepository},
 * {@code OwnerReplicaRepository} (propietario existente y activo, {@code is_active}),
 * {@code IssuedAlertRepository}, {@code OutboxRepository}, {@code AuditLogRepository},
 * {@code ProcessedEventRepository}, {@code JobRunRepository} y {@link BusinessClock}.
 */
package co.edu.univalle.fleetops.document.service.application.port.out;
