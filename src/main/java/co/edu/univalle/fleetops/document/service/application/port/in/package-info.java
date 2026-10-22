/**
 * Puertos de entrada (casos de uso).
 *
 * <p><b>Rol:</b> interfaces que invocan los adaptadores de entrada: REST, consumidor del broker y scheduler.
 *
 * <p><b>Patrones:</b> Use Case (Hexagonal Port), Command y Read Model.
 *
 * <p><b>Contenido previsto:</b>
 * <ul>
 *   <li>Comandos: {@code RegisterDocumentUseCase} (HU-DOC-01/02), {@code CorrectDocumentUseCase} y
 *       {@code CancelDocumentUseCase} (HU-DOC-04), {@code RenewDocumentUseCase} (HU-DOC-05).</li>
 *   <li>Consultas: {@code GetDocumentUseCase}, {@code ListDocumentsUseCase} (HU-DOC-03),
 *       {@code ExpiringSoonQuery} (HU-DOC-08), {@code ComplianceQuery} (HU-DOC-09).</li>
 *   <li>Procesos: {@code CheckExpirationsUseCase} (HU-DOC-06/07), {@code SyncOwnerReplicaUseCase}
 *       (HU-DOC-10).</li>
 * </ul>
 */
package co.edu.univalle.fleetops.document.service.application.port.in;
