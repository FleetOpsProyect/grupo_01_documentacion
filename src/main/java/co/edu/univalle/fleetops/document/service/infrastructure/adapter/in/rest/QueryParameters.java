package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import co.edu.univalle.fleetops.document.service.application.port.in.InvalidQueryParameterException;
import co.edu.univalle.fleetops.document.service.application.port.in.PageQuery;

/**
 * Convierte los parámetros de la URL, que llegan como texto, a números o enums. Si el formato es inválido, lanza
 * {@link InvalidQueryParameterException} con el mensaje de la historia de usuario. Los rangos (por ejemplo, size de
 * 1 a 100) no se validan aquí, sino en {@link PageQuery} y en cada consulta.
 */
final class QueryParameters {

	private QueryParameters() {
	}

	/**
	 * @param value texto recibido; null si el parámetro no llegó
	 * @param defaultValue valor que se usa si el parámetro no llegó
	 * @param errorMessage mensaje si el texto no es un número entero
	 */
	static int toInt(String value, int defaultValue, String errorMessage) {
		if (value == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException ex) {
			throw new InvalidQueryParameterException(errorMessage);
		}
	}

	/**
	 * @param value texto recibido; null si el parámetro no llegó
	 * @param type enum con los valores permitidos
	 * @param name nombre del filtro en la URL, para el mensaje de error
	 * @return el valor del enum, o null si el parámetro no llegó
	 */
	static <E extends Enum<E>> E toEnum(String value, Class<E> type, String name) {
		if (value == null) {
			return null;
		}
		try {
			return Enum.valueOf(type, value);
		}
		catch (IllegalArgumentException ex) {
			throw InvalidQueryParameterException.invalidFilter(name);
		}
	}

	/**
	 * Arma la página con page y size. Si no llegan, usa 0 y 20.
	 */
	static PageQuery toPageQuery(String page, String size) {
		return new PageQuery(toInt(page, PageQuery.DEFAULT_PAGE, PageQuery.INVALID_PAGE),
				toInt(size, PageQuery.DEFAULT_SIZE, PageQuery.INVALID_SIZE));
	}

}
