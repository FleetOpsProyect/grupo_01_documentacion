package co.edu.univalle.fleetops.document.service.application.port.in;

import java.util.List;
import java.util.function.Function;

/**
 * Una página de resultados con sus datos de paginación. Sirve para cualquier listado paginado
 * (HU-DOC-03 y HU-DOC-08).
 *
 * @param content elementos de la página; lista vacía si no hay resultados
 * @param page número de página, desde 0
 * @param size cantidad de elementos por página
 * @param totalElements total de resultados, sumando todas las páginas
 * @param totalPages total de páginas
 * @param <T> tipo de los elementos
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public PageResult {
		// Copia que no se puede modificar
		content = List.copyOf(content);
	}

	/**
	 * Convierte cada elemento a otro tipo y conserva los datos de paginación.
	 *
	 * @param mapper función que convierte cada elemento
	 * @param <R> tipo de los elementos convertidos
	 */
	public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
		return new PageResult<>(this.content.stream().<R>map(mapper).toList(), this.page, this.size,
				this.totalElements, this.totalPages);
	}

}
