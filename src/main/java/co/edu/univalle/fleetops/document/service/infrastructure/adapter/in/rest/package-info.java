/**
 * Adaptador de entrada REST (DAS sec. 7).
 *
 * <p><b>Rol:</b> controllers bajo {@code /documents}, DTOs en snake_case con Bean Validation, mappers,
 * traducción de errores y correlación de peticiones.
 *
 * <p><b>Patrones:</b> Adapter, DTO, Mapper, Problem Details (RFC 9457) y Correlation Identifier.
 *
 * <p><b>Convenciones (DAS sec. 7):</b> 400 para solicitudes mal formadas, 404 para recursos inexistentes,
 * 409 para conflictos y 422 para validaciones de negocio; mensajes en español con detalle por campo en
 * {@code detalles}; fechas YYYY-MM-DD; paginación con page desde 0 y size 20 (máximo 100);
 * {@code X-Correlation-Id} en toda respuesta.
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;
