package co.edu.univalle.fleetops.document.service.application.port.in;

/**
 * Página que pide el usuario. Si los valores están fuera de rango, el constructor lanza
 * {@link InvalidQueryParameterException}. Sirve para cualquier listado paginado (HU-DOC-03 y HU-DOC-08).
 *
 * @param page número de página, desde 0
 * @param size cantidad de elementos por página, de 1 a 100
 */
public record PageQuery(int page, int size) {

	public static final int DEFAULT_PAGE = 0;

	public static final int DEFAULT_SIZE = 20;

	public static final int MAX_SIZE = 100;

	public static final String INVALID_PAGE = "El parámetro page debe ser un entero mayor o igual a 0";

	/** Mensaje definido en HU-DOC-03, criterio 4. */
	public static final String INVALID_SIZE = "El parámetro size debe estar entre 1 y 100";

	public PageQuery {
		if (page < 0) {
			throw new InvalidQueryParameterException(INVALID_PAGE);
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new InvalidQueryParameterException(INVALID_SIZE);
		}
	}

}
