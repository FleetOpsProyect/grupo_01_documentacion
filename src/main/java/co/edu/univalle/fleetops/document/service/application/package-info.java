/**
 * Capa de aplicación: núcleo de la arquitectura hexagonal.
 *
 * <p><b>Rol:</b> orquesta los casos de uso del servicio. Define los puertos de entrada (lo que el servicio
 * ofrece) y los de salida (lo que el servicio necesita), e implementa los casos de uso sobre el dominio.
 *
 * <p><b>Regla de dependencia:</b> depende solo de {@code domain}. No conoce Spring, JPA ni el broker; la
 * transaccionalidad se aplica desde {@code infrastructure.config} (Decorator).
 *
 * <p><b>Trazabilidad:</b> diagrama de arquetipo (DAS sec. 15), bloques «Puertos de entrada», «Servicios de
 * aplicación» y «Puertos de salida».
 */
package co.edu.univalle.fleetops.document.service.application;
