package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	@ParameterizedTest
	@CsvSource({ "expirationDate, expiration_date", "documentType, document_type", "ownerId, owner_id",
			"status, status" })
	void should_name_fields_as_in_the_json_contract(String javaField, String contractField) {
		// Act
		String field = GlobalExceptionHandler.toSnakeCase(javaField);

		// Assert
		assertThat(field).isEqualTo(contractField);
	}

}
