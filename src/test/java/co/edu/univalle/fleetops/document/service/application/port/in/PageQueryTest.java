package co.edu.univalle.fleetops.document.service.application.port.in;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

	@Test
	void should_accept_the_limits_of_page_and_size() {
		assertThat(new PageQuery(0, 1)).extracting(PageQuery::page, PageQuery::size).containsExactly(0, 1);
		assertThat(new PageQuery(7, 100).size()).isEqualTo(100);
	}

	@Test
	void should_reject_a_negative_page() {
		assertThatThrownBy(() -> new PageQuery(-1, 20))
			.isInstanceOf(InvalidQueryParameterException.class)
			.hasMessage("El parámetro page debe ser un entero mayor o igual a 0");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 101 })
	void should_reject_a_size_out_of_range(int size) {
		assertThatThrownBy(() -> new PageQuery(0, size))
			.isInstanceOf(InvalidQueryParameterException.class)
			.hasMessage("El parámetro size debe estar entre 1 y 100");
	}

	@Test
	void should_map_the_content_and_keep_the_pagination_data() {
		PageResult<Integer> numbers = new PageResult<>(List.of(1, 2), 1, 2, 5, 3);

		PageResult<String> texts = numbers.map(number -> "n" + number);

		assertThat(texts.content()).containsExactly("n1", "n2");
		assertThat(texts).extracting(PageResult::page, PageResult::size, PageResult::totalElements,
				PageResult::totalPages)
			.containsExactly(1, 2, 5L, 3);
	}

	@Test
	void should_keep_an_immutable_copy_of_the_content() {
		List<Integer> source = new java.util.ArrayList<>(List.of(1));
		PageResult<Integer> page = new PageResult<>(source, 0, 20, 1, 1);

		source.add(2);

		assertThat(page.content()).containsExactly(1);
		assertThatThrownBy(() -> page.content().add(3)).isInstanceOf(UnsupportedOperationException.class);
	}

}
