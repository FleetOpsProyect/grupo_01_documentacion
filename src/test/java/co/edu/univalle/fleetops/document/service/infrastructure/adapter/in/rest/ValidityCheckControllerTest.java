package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase.EligibleOwnerView;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase.ValidDocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase.ValidityCheckPage;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase.ValidityCheckQuery;
import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ValidityCheckController.class)
@ActiveProfiles("test")
class ValidityCheckControllerTest {

	private static final String PATH = "/documents/validity-check";

	private static final UUID VEHICLE_ID = UUID.fromString("01960000-0000-7000-8000-000000000001");

	private static final UUID OTHER_ID = UUID.fromString("01960000-0000-7000-8000-000000000002");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ValidityCheckUseCase validityCheck;

	@Test
	void should_respond_200_with_pagination_and_eligible_owners_in_snake_case() throws Exception {
		// Arrange
		given(this.validityCheck.check(any())).willReturn(new ValidityCheckPage(0, 20, 1, 1,
				List.of(new EligibleOwnerView(VEHICLE_ID, "ABC123", null,
						List.of(new ValidDocumentView(DocumentTypeCode.SOAT, LocalDate.of(2027, 9, 30)),
								new ValidDocumentView(DocumentTypeCode.PROPERTY_CARD, null))))));

		// Act + Assert (criterio 3, ejemplo del Excel de endpoints)
		this.mockMvc.perform(get(PATH).param("owner_type", "VEHICLE").param("valid_until", "2026-12-31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.total_elements").value(1))
			.andExpect(jsonPath("$.total_pages").value(1))
			.andExpect(jsonPath("$.data[0].owner_id").value(VEHICLE_ID.toString()))
			.andExpect(jsonPath("$.data[0].license_plate").value("ABC123"))
			.andExpect(jsonPath("$.data[0].full_name").value(nullValue()))
			.andExpect(jsonPath("$.data[0].documents[0].document_type").value("SOAT"))
			.andExpect(jsonPath("$.data[0].documents[0].expiration_date").value("2027-09-30"))
			.andExpect(jsonPath("$.data[0].documents[1].document_type").value("PROPERTY_CARD"))
			.andExpect(jsonPath("$.data[0].documents[1].expiration_date").value(nullValue()));

		// Página 0 y tamaño 20 por defecto, sin filtro de propietarios
		ArgumentCaptor<ValidityCheckQuery> query = ArgumentCaptor.forClass(ValidityCheckQuery.class);
		verify(this.validityCheck).check(query.capture());
		assertThat(query.getValue())
			.isEqualTo(new ValidityCheckQuery(OwnerType.VEHICLE, LocalDate.of(2026, 12, 31), Set.of(), 0, 20));
	}

	@Test
	void should_pass_the_owner_ids_list_and_the_page() throws Exception {
		// Arrange
		given(this.validityCheck.check(any())).willReturn(new ValidityCheckPage(1, 5, 0, 0, List.of()));

		// Act: lista separada por comas, con espacios y un repetido
		this.mockMvc.perform(get(PATH).param("owner_type", "DRIVER")
			.param("valid_until", "2026-12-31")
			.param("owner_ids", VEHICLE_ID + ", " + OTHER_ID + "," + VEHICLE_ID)
			.param("page", "1")
			.param("size", "5")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());

		// Assert
		ArgumentCaptor<ValidityCheckQuery> query = ArgumentCaptor.forClass(ValidityCheckQuery.class);
		verify(this.validityCheck).check(query.capture());
		assertThat(query.getValue()).isEqualTo(new ValidityCheckQuery(OwnerType.DRIVER, LocalDate.of(2026, 12, 31),
				Set.of(VEHICLE_ID, OTHER_ID), 1, 5));
	}

	@Test
	void should_treat_a_blank_owner_ids_as_omitted() {
		assertThat(ValidityCheckController.parseOwnerIds(" ")).isEmpty();
		assertThat(ValidityCheckController.parseOwnerIds(null)).isEmpty();
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
			"sin owner_type|                |2026-12-31|           |0 |20|El parámetro 'owner_type' es obligatorio y debe ser VEHICLE o DRIVER.",
			"owner_type inválido|TRUCK      |2026-12-31|           |0 |20|El parámetro 'owner_type' es obligatorio y debe ser VEHICLE o DRIVER.",
			"sin valid_until|VEHICLE        |          |           |0 |20|El parámetro 'valid_until' es obligatorio y debe tener formato YYYY-MM-DD.",
			"fecha inválida|VEHICLE         |31/12/2026|           |0 |20|El parámetro 'valid_until' es obligatorio y debe tener formato YYYY-MM-DD.",
			"UUID inválido|VEHICLE          |2026-12-31|abc        |0 |20|El parámetro 'owner_ids' debe ser una lista de UUID separados por coma.",
			"elemento vacío|VEHICLE         |2026-12-31|01960000-0000-7000-8000-000000000001,|0|20|El parámetro 'owner_ids' debe ser una lista de UUID separados por coma.",
			"page negativa|VEHICLE          |2026-12-31|           |-1|20|El parámetro 'page' debe ser un entero mayor o igual a 0.",
			"page no numérica|VEHICLE       |2026-12-31|           |x |20|El parámetro 'page' debe ser un entero mayor o igual a 0.",
			"size cero|VEHICLE              |2026-12-31|           |0 |0 |El parámetro 'size' debe ser un entero entre 1 y 100.",
			"size mayor que 100|VEHICLE     |2026-12-31|           |0 |101|El parámetro 'size' debe ser un entero entre 1 y 100.",
			"size no numérico|VEHICLE       |2026-12-31|           |0 |x |El parámetro 'size' debe ser un entero entre 1 y 100."
	})
	void should_respond_400_for_an_invalid_parameter(String scenario, String ownerType, String validUntil,
			String ownerIds, String page, String size, String detail) throws Exception {
		var request = get(PATH).param("page", page).param("size", size);
		if (ownerType != null) {
			request.param("owner_type", ownerType);
		}
		if (validUntil != null) {
			request.param("valid_until", validUntil);
		}
		if (ownerIds != null) {
			request.param("owner_ids", ownerIds);
		}

		// Criterio 4
		this.mockMvc.perform(request)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Parámetro inválido"))
			.andExpect(jsonPath("$.detail").value(detail))
			.andExpect(jsonPath("$.instance").value(PATH));
		verifyNoInteractions(this.validityCheck);
	}

	@Test
	void should_respond_400_for_more_than_100_owner_ids() throws Exception {
		String ids = IntStream.rangeClosed(0, 100)
			.mapToObj(i -> UUID.randomUUID().toString())
			.collect(Collectors.joining(","));

		this.mockMvc
			.perform(get(PATH).param("owner_type", "VEHICLE")
				.param("valid_until", "2026-12-31")
				.param("owner_ids", ids))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("El parámetro 'owner_ids' admite como máximo 100 UUID."));
	}

	@Test
	void should_respond_422_for_a_date_before_today() throws Exception {
		// Arrange (criterio 4)
		String detail = "valid_until no puede ser anterior a la fecha actual (2026-11-24).";
		given(this.validityCheck.check(any())).willThrow(new BusinessRuleViolationException(detail));

		// Act + Assert
		this.mockMvc.perform(get(PATH).param("owner_type", "VEHICLE").param("valid_until", "2026-11-23"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.title").value("Regla de negocio no cumplida"))
			.andExpect(jsonPath("$.detail").value(detail));
	}

}
