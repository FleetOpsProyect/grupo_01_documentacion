package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonView;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonView.ExpiringDocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.PageQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.PageResult;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpiringSoonController.class)
@ActiveProfiles("test")
class ExpiringSoonControllerTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

	private static final UUID VEHICLE_DOCUMENT = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID DRIVER_DOCUMENT = UUID.fromString("019a2f50-1111-7abc-8def-222233334444");

	private static final UUID VEHICLE_ID = UUID.fromString("01960000-0000-7000-8000-000000000001");

	private static final UUID DRIVER_ID = UUID.fromString("01960000-0000-7000-8000-000000000101");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ExpiringSoonUseCase expiringSoon;

	@Test
	void should_respond_200_with_the_contract_body_and_the_default_parameters() throws Exception {
		// Arrange: ejemplo de la hoja «Endpoints»
		given(this.expiringSoon.findExpiringSoon(any())).willReturn(new ExpiringSoonView(30, TODAY,
				new PageResult<>(List.of(
						new ExpiringDocumentView(VEHICLE_DOCUMENT, DocumentTypeCode.TECHNICAL_INSPECTION,
								OwnerType.VEHICLE, VEHICLE_ID, "RTM-554433", "ABC123", null, LocalDate.of(2026, 11, 5),
								10, DocumentStatus.EXPIRING_SOON),
						new ExpiringDocumentView(DRIVER_DOCUMENT, DocumentTypeCode.DRIVER_LICENSE, OwnerType.DRIVER,
								DRIVER_ID, "LIC-1020304050", null, "Conductor Demo Activo",
								LocalDate.of(2026, 11, 20), 25, DocumentStatus.EXPIRING_SOON)),
						0, 20, 2, 1)));

		// Act + Assert
		this.mockMvc.perform(get("/documents/expiring-soon"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.horizon_days").value(30))
			.andExpect(jsonPath("$.evaluated_on").value("2026-10-26"))
			.andExpect(jsonPath("$.content", hasSize(2)))
			.andExpect(jsonPath("$.content[0].document_id").value(VEHICLE_DOCUMENT.toString()))
			.andExpect(jsonPath("$.content[0].document_type").value("TECHNICAL_INSPECTION"))
			.andExpect(jsonPath("$.content[0].owner_type").value("VEHICLE"))
			.andExpect(jsonPath("$.content[0].owner_id").value(VEHICLE_ID.toString()))
			.andExpect(jsonPath("$.content[0].document_number").value("RTM-554433"))
			.andExpect(jsonPath("$.content[0].license_plate").value("ABC123"))
			.andExpect(jsonPath("$.content[0].full_name").value(nullValue()))
			.andExpect(jsonPath("$.content[0].expiration_date").value("2026-11-05"))
			.andExpect(jsonPath("$.content[0].days_remaining").value(10))
			.andExpect(jsonPath("$.content[0].status").value("EXPIRING_SOON"))
			.andExpect(jsonPath("$.content[1].license_plate").value(nullValue()))
			.andExpect(jsonPath("$.content[1].full_name").value("Conductor Demo Activo"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.total_elements").value(2))
			.andExpect(jsonPath("$.total_pages").value(1));

		ArgumentCaptor<ExpiringSoonQuery> query = ArgumentCaptor.forClass(ExpiringSoonQuery.class);
		verify(this.expiringSoon).findExpiringSoon(query.capture());
		assertThat(query.getValue()).isEqualTo(new ExpiringSoonQuery(30, null, null, new PageQuery(0, 20)));
	}

	@Test
	void should_pass_the_filters_and_page_to_the_use_case() throws Exception {
		given(this.expiringSoon.findExpiringSoon(any()))
			.willReturn(new ExpiringSoonView(15, TODAY, new PageResult<>(List.of(), 2, 50, 0, 0)));

		this.mockMvc.perform(get("/documents/expiring-soon").param("days", "15").param("document_type", "SOAT")
			.param("owner_type", "VEHICLE")
			.param("page", "2")
			.param("size", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(0)))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.size").value(50));

		ArgumentCaptor<ExpiringSoonQuery> query = ArgumentCaptor.forClass(ExpiringSoonQuery.class);
		verify(this.expiringSoon).findExpiringSoon(query.capture());
		assertThat(query.getValue()).isEqualTo(
				new ExpiringSoonQuery(15, DocumentTypeCode.SOAT, OwnerType.VEHICLE, new PageQuery(2, 50)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "", "0", "366", "1.5" })
	void should_respond_400_when_days_is_not_an_integer_between_1_and_365(String days) throws Exception {
		this.mockMvc.perform(get("/documents/expiring-soon").param("days", days))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Parámetro inválido"))
			.andExpect(jsonPath("$.detail").value("El parámetro days debe ser un entero entre 1 y 365"));

		verifyNoInteractions(this.expiringSoon);
	}

	@ParameterizedTest
	@CsvSource({ "document_type, PROPERTY_CARD", "document_type, POLIZA", "document_type, soat",
			"owner_type, TRAILER" })
	void should_respond_400_when_a_filter_is_out_of_the_catalog(String name, String value) throws Exception {
		this.mockMvc.perform(get("/documents/expiring-soon").param(name, value))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Valor de filtro no válido: " + name));

		verifyNoInteractions(this.expiringSoon);
	}

	@ParameterizedTest
	@CsvSource({ "page, -1, El parámetro page debe ser un entero mayor o igual a 0",
			"page, x, El parámetro page debe ser un entero mayor o igual a 0",
			"size, 0, El parámetro size debe estar entre 1 y 100",
			"size, 101, El parámetro size debe estar entre 1 y 100",
			"size, x, El parámetro size debe estar entre 1 y 100" })
	void should_respond_400_when_page_or_size_are_invalid(String name, String value, String detail)
			throws Exception {
		this.mockMvc.perform(get("/documents/expiring-soon").param(name, value))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(detail));

		verifyNoInteractions(this.expiringSoon);
	}

}
