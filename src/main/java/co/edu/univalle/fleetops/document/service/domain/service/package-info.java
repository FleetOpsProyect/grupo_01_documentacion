/**
 * Servicios de dominio.
 *
 * <p><b>Rol:</b> reglas de negocio que no pertenecen a una sola entidad.
 *
 * <p><b>Patrones:</b> Domain Service y Policy/Strategy.
 *
 * <p><b>Contenido previsto:</b>
 * <ul>
 *   <li>{@code DocumentStatusCalculator}: <b>regla única</b> de cálculo de estado por fecha y días restantes
 *       (ADR-DOC-01, DAS sec. 6.2). Si days_remaining es menor que 0, el documento está EXPIRED; si está
 *       entre 0 y el umbral mayor, EXPIRING_SOON; si es mayor, VALID. Los documentos sin vencimiento quedan
 *       en VALID. La comparten el registro, las consultas y el job.</li>
 *   <li>{@code AlertThresholdPolicy}: umbral que corresponde emitir y umbrales que se marcan como emitidos
 *       (ADR-DOC-03).</li>
 *   <li>{@code ComplianceEvaluator}: cumplimiento documental de un vehículo o conductor (HU-DOC-09).</li>
 * </ul>
 */
package co.edu.univalle.fleetops.document.service.domain.service;
