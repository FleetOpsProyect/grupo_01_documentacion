/**
 * Excepciones de negocio.
 *
 * <p><b>Rol:</b> expresan violaciones de reglas del dominio sin conocer HTTP. El adaptador REST las traduce
 * a los códigos acordados en el DAS (sec. 7): 422 para validaciones de negocio, 409 para conflictos de
 * unicidad o de estado y 404 para recursos inexistentes.
 */
package co.edu.univalle.fleetops.document.service.domain.exception;
