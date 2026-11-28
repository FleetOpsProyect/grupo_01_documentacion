package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.univalle.fleetops.document.service.application.port.in.ComplianceUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.GetDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ListDocumentQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.ListDocumentsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.PageQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.PageResult;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentUseCase;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.application.port.in.CancelDocumentUseCase;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@ActiveProfiles("test")
class DocumentListControllerTest {

	private static final UUID DOCUMENT_ID = UUID.randomUUID();

	private static final UUID OWNER_ID = UUID.randomUUID();

	private static final LocalDate FROM = LocalDate.of(2026, 11, 1);

	private static final LocalDate TO = LocalDate.of(2026, 11, 30);

	private static final ListDocumentQuery DEFAULT_QUERY = new ListDocumentQuery(
			null, null, null, null, null, null, new PageQuery(0, 20));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegisterDocumentUseCase registerDocument;

	@MockitoBean
	private GetDocumentUseCase getDocument;

	@MockitoBean
	private ComplianceUseCase compliance;

	@MockitoBean
	private ListDocumentsUseCase listDocuments;

	@MockitoBean
	private CancelDocumentUseCase cancelDocument;

	@Test
	void should_return_documents_with_default_pagination() throws Exception {
		given(this.listDocuments.listDocuments(DEFAULT_QUERY))
				.willReturn(new PageResult<>(
						List.of(document(DocumentStatus.EXPIRED, -6)), 0, 20, 1, 1));

		this.mockMvc.perform(get("/documents"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].document_id").value(DOCUMENT_ID.toString()))
				.andExpect(jsonPath("$.content[0].document_type").value("SOAT"))
				.andExpect(jsonPath("$.content[0].owner_type").value("VEHICLE"))
				.andExpect(jsonPath("$.content[0].owner_id").value(OWNER_ID.toString()))
				.andExpect(jsonPath("$.content[0].document_number").value("SOAT-12345"))
				.andExpect(jsonPath("$.content[0].issuing_entity").value("Entidad Ejemplo"))
				.andExpect(jsonPath("$.content[0].issue_date").value("2025-11-20"))
				.andExpect(jsonPath("$.content[0].expiration_date").value("2026-11-20"))
				.andExpect(jsonPath("$.content[0].license_category").value(nullValue()))
				.andExpect(jsonPath("$.content[0].status").value("EXPIRED"))
				.andExpect(jsonPath("$.content[0].days_remaining").value(-6))
				.andExpect(jsonPath("$.content[0].previous_document_id").value(nullValue()))
				.andExpect(jsonPath("$.content[0].cancellation_reason").value(nullValue()))
				.andExpect(jsonPath("$.content[0].cancelled_at").value(nullValue()))
				.andExpect(jsonPath("$.content[0].created_at").value("2025-11-20T10:00:00-05:00"))
				.andExpect(jsonPath("$.content[0].updated_at").value("2025-11-20T10:00:00-05:00"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.total_elements").value(1))
				.andExpect(jsonPath("$.total_pages").value(1));

		verify(this.listDocuments).listDocuments(DEFAULT_QUERY);
	}

	@Test
	void should_pass_all_filters_and_pagination_to_the_use_case() throws Exception {
		ListDocumentQuery query = new ListDocumentQuery(
				OwnerType.VEHICLE, OWNER_ID, DocumentTypeCode.SOAT,
				DocumentStatus.EXPIRED, FROM, TO, new PageQuery(1, 10));

		given(this.listDocuments.listDocuments(query))
				.willReturn(new PageResult<>(
						List.of(document(DocumentStatus.EXPIRED, -6)), 1, 10, 11, 2));

		this.mockMvc.perform(get("/documents")
						.param("owner_type", "VEHICLE")
						.param("owner_id", OWNER_ID.toString())
						.param("document_type", "SOAT")
						.param("status", "EXPIRED")
						.param("expiration_date_from", FROM.toString())
						.param("expiration_date_to", TO.toString())
						.param("page", "1")
						.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.total_elements").value(11))
				.andExpect(jsonPath("$.total_pages").value(2));

		verify(this.listDocuments).listDocuments(query);
	}

	@ParameterizedTest
	@CsvSource({
			"owner_type, TRAILER",
			"owner_type, vehicle",
			"owner_id, ABC123",
			"owner_id, 1-1-1-1-1",
			"document_type, POLIZA",
			"status, ACTIVE",
			"expiration_date_from, 26/11/2026",
			"expiration_date_to, 2026-02-30"
	})
	void should_reject_invalid_filters(String name, String value) throws Exception {
		this.mockMvc.perform(get("/documents").param(name, value))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Valor de filtro no válido: " + name));

		verifyNoInteractions(this.listDocuments);
	}

	@Test
	void should_reject_owner_id_without_owner_type() throws Exception {
		this.mockMvc.perform(get("/documents")
						.param("owner_id", OWNER_ID.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Valor de filtro no válido: owner_type"));

		verifyNoInteractions(this.listDocuments);
	}

	@Test
	void should_reject_an_inverted_expiration_date_range() throws Exception {
		this.mockMvc.perform(get("/documents")
						.param("expiration_date_from", TO.toString())
						.param("expiration_date_to", FROM.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Valor de filtro no válido: expiration_date_from"));

		verifyNoInteractions(this.listDocuments);
	}

	@ParameterizedTest
	@CsvSource({
			"size, 0",
			"size, -1",
			"size, 101",
			"size, abc",
			"size, 1.5"
	})
	void should_reject_invalid_page_sizes(String name, String value) throws Exception {
		this.mockMvc.perform(get("/documents").param(name, value))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(PageQuery.INVALID_SIZE));

		verifyNoInteractions(this.listDocuments);
	}

	@ParameterizedTest
	@CsvSource({
			"page, -1",
			"page, abc",
			"page, 1.5"
	})
	void should_reject_invalid_page_numbers(String name, String value) throws Exception {
		this.mockMvc.perform(get("/documents").param(name, value))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(PageQuery.INVALID_PAGE));

		verifyNoInteractions(this.listDocuments);
	}

	@Test
	void should_accept_cancelled_as_an_explicit_status_filter() throws Exception {
		ListDocumentQuery query = new ListDocumentQuery(
				null, null, null, DocumentStatus.CANCELLED,
				null, null, new PageQuery(0, 20));

		given(this.listDocuments.listDocuments(query))
				.willReturn(new PageResult<>(
						List.of(document(DocumentStatus.CANCELLED, null)), 0, 20, 1, 1));

		this.mockMvc.perform(get("/documents").param("status", "CANCELLED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
				.andExpect(jsonPath("$.content[0].days_remaining").value(nullValue()));

		verify(this.listDocuments).listDocuments(query);
	}

	@Test
	void should_return_200_with_empty_content_when_no_documents_match() throws Exception {
		given(this.listDocuments.listDocuments(DEFAULT_QUERY))
				.willReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

		this.mockMvc.perform(get("/documents"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.total_elements").value(0))
				.andExpect(jsonPath("$.total_pages").value(0));

		verify(this.listDocuments).listDocuments(DEFAULT_QUERY);
	}

	private static DocumentView document(DocumentStatus status, Integer daysRemaining) {
		Instant createdAt = Instant.parse("2025-11-20T15:00:00Z");

		return new DocumentView(
				DOCUMENT_ID,
				DocumentTypeCode.SOAT,
				OwnerType.VEHICLE,
				OWNER_ID,
				"SOAT-12345",
				"Entidad Ejemplo",
				LocalDate.of(2025, 11, 20),
				LocalDate.of(2026, 11, 20),
				null,
				status,
				daysRemaining,
				null,
				null,
				null,
				createdAt,
				createdAt);
	}

}
