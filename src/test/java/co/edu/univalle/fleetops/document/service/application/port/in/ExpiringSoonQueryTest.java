package co.edu.univalle.fleetops.document.service.application.port.in;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpiringSoonQueryTest {

	private static final PageQuery FIRST_PAGE = new PageQuery(0, 20);

	@ParameterizedTest
	@ValueSource(ints = { 1, 30, 365 })
	void should_accept_a_horizon_between_1_and_365_days(int days) {
		assertThat(new ExpiringSoonQuery(days, null, null, FIRST_PAGE).days()).isEqualTo(days);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 0, 366 })
	void should_reject_a_horizon_out_of_range(int days) {
		assertThatThrownBy(() -> new ExpiringSoonQuery(days, null, null, FIRST_PAGE))
			.isInstanceOf(InvalidQueryParameterException.class)
			.hasMessage("El parámetro days debe ser un entero entre 1 y 365");
	}

	@ParameterizedTest
	@EnumSource(value = DocumentTypeCode.class, names = "PROPERTY_CARD", mode = EnumSource.Mode.EXCLUDE)
	void should_accept_the_document_types_that_expire(DocumentTypeCode documentType) {
		ExpiringSoonQuery query = new ExpiringSoonQuery(30, documentType, OwnerType.VEHICLE, FIRST_PAGE);

		assertThat(query.documentType()).isEqualTo(documentType);
		assertThat(query.ownerType()).isEqualTo(OwnerType.VEHICLE);
	}

	@Test
	void should_reject_property_card_because_it_does_not_expire() {
		assertThatThrownBy(() -> new ExpiringSoonQuery(30, DocumentTypeCode.PROPERTY_CARD, null, FIRST_PAGE))
			.isInstanceOf(InvalidQueryParameterException.class)
			.hasMessage("Valor de filtro no válido: document_type");
	}

	@Test
	void should_require_the_page() {
		assertThatNullPointerException().isThrownBy(() -> new ExpiringSoonQuery(30, null, null, null))
			.withMessage("pageQuery");
	}

}
