/**
 * Adaptador de entrada programado: job de vigencia (HU-DOC-06/07, ADR-DOC-02).
 *
 * <p><b>Estado:</b> vacío en esta versión.
 *
 * <p><b>Contenido previsto:</b> ejecución diaria a la 01:00 hora de Bogotá (-05:00; 06:00 UTC) y recuperación al
 * arrancar, invocando {@code CheckExpirationsUseCase}. La protección contra duplicados vive en la base de
 * datos ({@code issued_alerts}, {@code scheduled_job_runs}) y no en memoria.
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.scheduler;
