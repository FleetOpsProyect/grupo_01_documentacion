package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO del cuerpo de {@code POST /documents} (DAS sec. 7, hoja «Endpoints» del documento de endpoints). Los nombres
 * viajan en snake_case por la estrategia global de Jackson.
 *
 * <p>Aquí solo se valida el formato (422 con detalle por campo). Las reglas que dependen del tipo, como la fecha de
 * vencimiento obligatoria o la categoría de la licencia, las valida el dominio.
 */
public record RegisterDocumentRequest(

		@NotBlank(message = "Es obligatorio")
		@Pattern(regexp = "SOAT|TECHNICAL_INSPECTION|PROPERTY_CARD|DRIVER_LICENSE",
				message = "Debe ser SOAT, TECHNICAL_INSPECTION, PROPERTY_CARD o DRIVER_LICENSE")
		String documentType,

		@NotBlank(message = "Es obligatorio")
		@Pattern(regexp = "VEHICLE|DRIVER", message = "Debe ser VEHICLE o DRIVER")
		String ownerType,

		@NotNull(message = "Es obligatorio")
		UUID ownerId,

		@NotBlank(message = "Es obligatorio")
		@Size(max = 50, message = "No puede tener más de 50 caracteres")
		String documentNumber,

		@NotBlank(message = "Es obligatorio")
		@Size(max = 150, message = "No puede tener más de 150 caracteres")
		String issuingEntity,

		@NotNull(message = "Es obligatoria")
		LocalDate issueDate,

		LocalDate expirationDate,

		@Size(max = 5, message = "No puede tener más de 5 caracteres")
		String licenseCategory) {

}
