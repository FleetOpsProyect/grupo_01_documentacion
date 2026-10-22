/**
 * Capa de dominio: núcleo de la arquitectura hexagonal.
 *
 * <p><b>Rol:</b> contiene el modelo y las reglas de negocio del servicio de Documentación y Seguros
 * (DAS sec. 6): el documento, sus tipos, sus estados y la regla única de cálculo de vigencia.
 *
 * <p><b>Regla de dependencia:</b> no depende de ninguna otra capa ni de ningún framework
 * (Spring, JPA, Jackson, broker). Solo usa Java estándar.
 *
 * <p><b>Trazabilidad:</b> diagrama de arquetipo (DAS sec. 15), bloque «Dominio: Document y reglas de vigencia».
 */
package co.edu.univalle.fleetops.document.service.domain;
