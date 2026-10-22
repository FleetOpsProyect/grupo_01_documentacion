/**
 * Adaptador de salida de reloj: fecha de negocio en America/Bogota (DAS sec. 5, ADR-DOC-01).
 *
 * <p><b>Rol:</b> implementa {@code BusinessClock}. Al aislar el reloj, las pruebas pueden fijar la fecha
 * (por ejemplo, vencimiento el 20 de octubre: EXPIRING_SOON ese día y EXPIRED desde el 21).
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.clock;
