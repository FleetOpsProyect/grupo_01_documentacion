/**
 * Adaptador de entrada programado: job de vigencia (HU-DOC-06, ADR-DOC-02).
 *
 * <p>{@link co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.scheduler.ExpirationCheckScheduler}
 * invoca {@code CheckExpirationsUseCase} a la 01:00 hora de Bogotá (-05:00; 06:00 UTC) y al arrancar, para recuperar
 * una ejecución pendiente. La protección contra duplicados vive en la base de datos ({@code scheduled_job_runs},
 * {@code issued_alerts}) y no en memoria, así que varias instancias pueden tener el job programado. Lo crea
 * {@code ExpirationJobConfiguration} solo con {@code fleetops.documents.expiration-job.enabled=true}.
 *
 * <p>HU-DOC-07 (paso a EXPIRED y document_expired_event) se agregará a la misma verificación.
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.scheduler;
