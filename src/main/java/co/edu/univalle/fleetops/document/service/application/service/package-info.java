/**
 * Servicios de aplicación.
 *
 * <p><b>Rol:</b> implementan los puertos de entrada. Validan, invocan al dominio, persisten mediante los
 * puertos de salida y registran el evento en el outbox, todo en la misma transacción (ADR-DOC-04/05).
 *
 * <p><b>Patrón:</b> Application Service. Clases Java planas, sin anotaciones de Spring; se cablean de forma
 * explícita en {@code infrastructure.config}.
 */
package co.edu.univalle.fleetops.document.service.application.service;
