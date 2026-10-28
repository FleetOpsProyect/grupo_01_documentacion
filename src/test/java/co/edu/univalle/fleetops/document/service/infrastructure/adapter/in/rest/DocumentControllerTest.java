package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentCommand;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentUseCase;
import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;
import co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@ActiveProfiles("test")
class DocumentControllerTest {

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID VEHICLE_ID = UUID.fromString("01960000-0000-7000-8000-000000000001");

	private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	private static final String SOAT_REQUEST = """
			{ "document_type": "SOAT", "owner_type": "VEHICLE",
			  "owner_id": "01960000-0000-7000-8000-000000000001",
			  "document_number": "AT-1300-0045-2026", "issuing_entity": "Seguros Demo S.A.",
			  "issue_date": "2026-10-01", "expiration_date": "2027-09-30" }
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegisterDocumentUseCase registerDocument;

	@Test
	void should_respond_201_with_location_snake_case_body_and_correlation_id() throws Exception {
		// Arrange
		given(this.registerDocument.register(any())).willReturn(soatView());

		// Act + Assert
		this.mockMvc.perform(post("/documents").contentType(MediaType.APPLICATION_JSON).content(SOAT_REQUEST))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/documents/" + DOCUMENT_ID))
			.andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)))
			.andExpect(jsonPath("$.document_id").value(DOCUMENT_ID.toString()))
			.andExpect(jsonPath("$.document_type").value("SOAT"))
			.andExpect(jsonPath("$.owner_type").value("VEHICLE"))
			.andExpect(jsonPath("$.document_number").value("AT-1300-0045-2026"))
			.andExpect(jsonPath("$.issue_date").value("2026-10-01"))
			.andExpect(jsonPath("$.expiration_date").value("2027-09-30"))
			.andExpect(jsonPath("$.license_category").value(nullValue()))
			.andExpect(jsonPath("$.status").value("VALID"))
			.andExpect(jsonPath("$.days_remaining").value(339))
			.andExpect(jsonPath("$.previous_document_id").value(nullValue()))
			.andExpect(jsonPath("$.cancellation_reason").value(nullValue()))
			.andExpect(jsonPath("$.cancelled_at").value(nullValue()))
			.andExpect(jsonPath("$.created_at").value("2026-10-26T15:04:05Z"));
	}

	@Test
	void should_map_the_request_to_the_register_command() throws Exception {
		// Arrange
		given(this.registerDocument.register(any())).willReturn(soatView());

		// Act
		this.mockMvc.perform(post("/documents").contentType(MediaType.APPLICATION_JSON).content(SOAT_REQUEST));

		// Assert
		ArgumentCaptor<RegisterDocumentCommand> command = ArgumentCaptor.forClass(RegisterDocumentCommand.class);
		verify(this.registerDocument).register(command.capture());
		assertThat(command.getValue()).isEqualTo(new RegisterDocumentCommand(DocumentTypeCode.SOAT,
				OwnerType.VEHICLE, VEHICLE_ID, "AT-1300-0045-2026", "Seguros Demo S.A.", LocalDate.of(2026, 10, 1),
				LocalDate.of(2027, 9, 30), null));
	}

	@Test
	void should_reuse_a_valid_correlation_id_sent_by_the_client() throws Exception {
		// Arrange
		given(this.registerDocument.register(any())).willReturn(soatView());
		String correlationId = "6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e";

		// Act + Assert
		this.mockMvc
			.perform(post("/documents").header("X-Correlation-Id", correlationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(SOAT_REQUEST))
			.andExpect(header().string("X-Correlation-Id", correlationId));
	}

	@Test
	void should_respond_422_with_snake_case_field_details_when_required_fields_are_missing() throws Exception {
		// Act + Assert
		this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "owner_type": "BOAT", "owner_id": "01960000-0000-7000-8000-000000000001",
						  "document_number": "AT-1", "issuing_entity": "Seguros Demo S.A." }
						"""))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.title").value("Datos inválidos"))
			.andExpect(jsonPath("$.detalles[*].campo").value(hasItems("document_type", "owner_type", "issue_date")));
		verifyNoInteractions(this.registerDocument);
	}

	@Test
	void should_respond_400_when_the_json_is_malformed() throws Exception {
		this.mockMvc.perform(post("/documents").contentType(MediaType.APPLICATION_JSON).content("{ \"document_type\": "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Solicitud mal formada"));
	}

	@Test
	void should_respond_400_when_a_date_has_an_invalid_format() throws Exception {
		this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON)
				.content(SOAT_REQUEST.replace("2027-09-30", "2027-13-45")))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(this.registerDocument);
	}

	@Test
	void should_respond_400_when_the_owner_id_is_not_a_uuid() throws Exception {
		this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON)
				.content(SOAT_REQUEST.replace("01960000-0000-7000-8000-000000000001", "not-a-uuid")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void should_respond_409_when_the_use_case_reports_a_conflict() throws Exception {
		// Arrange
		given(this.registerDocument.register(any()))
			.willThrow(new DomainConflictException("Ya existe un documento SOAT no anulado con ese document_number"));

		// Act + Assert
		this.mockMvc.perform(post("/documents").contentType(MediaType.APPLICATION_JSON).content(SOAT_REQUEST))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Ya existe un documento SOAT no anulado con ese document_number"));
	}

	@Test
	void should_respond_422_when_the_use_case_reports_a_business_rule_violation() throws Exception {
		// Arrange
		given(this.registerDocument.register(any()))
			.willThrow(new BusinessRuleViolationException("expiration_date es obligatoria para el tipo SOAT"));

		// Act + Assert
		this.mockMvc.perform(post("/documents").contentType(MediaType.APPLICATION_JSON).content(SOAT_REQUEST))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail").value("expiration_date es obligatoria para el tipo SOAT"));
	}

	private static DocumentView soatView() {
		Instant now = Instant.parse("2026-10-26T15:04:05Z");
		return new DocumentView(DOCUMENT_ID, DocumentTypeCode.SOAT, OwnerType.VEHICLE, VEHICLE_ID,
				"AT-1300-0045-2026", "Seguros Demo S.A.", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30), null,
				DocumentStatus.VALID, 339, now, now);
	}

}
