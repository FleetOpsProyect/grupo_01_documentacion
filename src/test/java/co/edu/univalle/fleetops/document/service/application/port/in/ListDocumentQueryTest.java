package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListDocumentsQueryTest {

	private static final PageQuery FIRST_PAGE = new PageQuery(0, 20);

	private static final UUID OWNER_ID = UUID.randomUUID();

	private static final LocalDate FROM = LocalDate.of(2026, 11, 1);

	private static final LocalDate TO = LocalDate.of(2026, 11, 30);

	@Test
	void should_accept_a_query_without_filters() {
		ListDocumentQuery query = new ListDocumentQuery(
				null, null, null, null, null, null, FIRST_PAGE);

		assertThat(query.ownerType()).isNull();
		assertThat(query.ownerId()).isNull();
		assertThat(query.documentType()).isNull();
		assertThat(query.status()).isNull();
		assertThat(query.expirationDateFrom()).isNull();
		assertThat(query.expirationDateTo()).isNull();
		assertThat(query.pageQuery()).isEqualTo(FIRST_PAGE);
	}

	@Test
	void should_accept_all_filters_combined() {
		ListDocumentQuery query = new ListDocumentQuery(
				OwnerType.VEHICLE, OWNER_ID, DocumentTypeCode.SOAT,
				DocumentStatus.VALID, FROM, TO, FIRST_PAGE);

		assertThat(query.ownerType()).isEqualTo(OwnerType.VEHICLE);
		assertThat(query.ownerId()).isEqualTo(OWNER_ID);
		assertThat(query.documentType()).isEqualTo(DocumentTypeCode.SOAT);
		assertThat(query.status()).isEqualTo(DocumentStatus.VALID);
		assertThat(query.expirationDateFrom()).isEqualTo(FROM);
		assertThat(query.expirationDateTo()).isEqualTo(TO);
		assertThat(query.pageQuery()).isEqualTo(FIRST_PAGE);
	}

	@Test
	void should_reject_owner_id_without_owner_type() {
		assertThatThrownBy(() -> new ListDocumentQuery(
				null, OWNER_ID, null, null, null, null, FIRST_PAGE))
				.isInstanceOf(InvalidQueryParameterException.class)
				.hasMessage("Valor de filtro no válido: owner_type");
	}

	@Test
	void should_accept_owner_type_without_owner_id() {
		ListDocumentQuery query = new ListDocumentQuery(
				OwnerType.DRIVER, null, null, null, null, null, FIRST_PAGE);

		assertThat(query.ownerType()).isEqualTo(OwnerType.DRIVER);
		assertThat(query.ownerId()).isNull();
	}

	@Test
	void should_reject_an_inverted_expiration_date_range() {
		assertThatThrownBy(() -> new ListDocumentQuery(
				null, null, null, null, TO, FROM, FIRST_PAGE))
				.isInstanceOf(InvalidQueryParameterException.class)
				.hasMessage("Valor de filtro no válido: expiration_date_from");
	}

	@Test
	void should_accept_equal_expiration_dates() {
		ListDocumentQuery query = new ListDocumentQuery(
				null, null, null, null, FROM, FROM, FIRST_PAGE);

		assertThat(query.expirationDateFrom())
				.isEqualTo(query.expirationDateTo());
	}

	@Test
	void should_accept_only_expiration_date_from() {
		ListDocumentQuery query = new ListDocumentQuery(
				null, null, null, null, FROM, null, FIRST_PAGE);

		assertThat(query.expirationDateFrom()).isEqualTo(FROM);
		assertThat(query.expirationDateTo()).isNull();
	}

	@Test
	void should_accept_only_expiration_date_to() {
		ListDocumentQuery query = new ListDocumentQuery(
				null, null, null, null, null, TO, FIRST_PAGE);

		assertThat(query.expirationDateFrom()).isNull();
		assertThat(query.expirationDateTo()).isEqualTo(TO);
	}

	@Test
	void should_accept_property_card_as_document_type() {
		ListDocumentQuery query = new ListDocumentQuery(
				null, null, DocumentTypeCode.PROPERTY_CARD,
				null, null, null, FIRST_PAGE);

		assertThat(query.documentType())
				.isEqualTo(DocumentTypeCode.PROPERTY_CARD);
	}

	@Test
	void should_require_the_page() {
		assertThatNullPointerException()
				.isThrownBy(() -> new ListDocumentQuery(
						null, null, null, null, null, null, null))
				.withMessage("pageQuery");
	}

}
