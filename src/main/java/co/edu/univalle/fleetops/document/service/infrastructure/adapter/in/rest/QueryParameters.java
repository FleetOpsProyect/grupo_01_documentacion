package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

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

	/**
	 * @param value texto recibido; null si el parámetro no llegó
	 * @param name nombre del filtro en la URL
	 * @return UUID convertido, o null si no llegó
	 */
	static UUID toUuid(String value, String name) {
		if (value == null) {
			return null;
		}

		return parseUuid(value, "Valor de filtro no válido: " + name);
	}

	/**
	 * @param value texto recibido
	 * @param errorMessage mensaje si el identificador es inválido
	 * @return UUID convertido
	 */
	static UUID parseUuid(String value, String errorMessage) {
		if (value == null) {
			throw new InvalidQueryParameterException(errorMessage);
		}

		UUID uuid;

		try {
			uuid = UUID.fromString(value);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidQueryParameterException(errorMessage);
		}

		if (!uuid.toString().equalsIgnoreCase(value)) {
			throw new InvalidQueryParameterException(errorMessage);
		}

		return uuid;
	}

	/**
	 * @param value texto recibido; null si el parámetro no llegó
	 * @param name nombre del filtro en la URL
	 * @return fecha convertida, o null si no llegó
	 */
	static LocalDate toDate(String value, String name) {
		if (value == null) {
			return null;
		}

		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException ex) {
			throw InvalidQueryParameterException.invalidFilter(name);
		}
	}

}
