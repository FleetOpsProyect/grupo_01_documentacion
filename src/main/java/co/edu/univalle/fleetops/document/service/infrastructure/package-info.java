/**
 * Capa de infraestructura: adaptadores de la arquitectura hexagonal.
 *
 * <p><b>Rol:</b> conecta el núcleo con el exterior. Los adaptadores de entrada invocan los puertos de
 * entrada; los adaptadores de salida implementan los puertos de salida.
 *
 * <p><b>Regla de dependencia:</b> depende de {@code application} y {@code domain}, nunca al revés.
 *
 * <p><b>Trazabilidad:</b> diagrama de arquetipo (DAS sec. 15), bloques «Adaptadores de entrada»,
 * «Adaptadores de salida» y «Transversal».
 */
package co.edu.univalle.fleetops.document.service.infrastructure;
