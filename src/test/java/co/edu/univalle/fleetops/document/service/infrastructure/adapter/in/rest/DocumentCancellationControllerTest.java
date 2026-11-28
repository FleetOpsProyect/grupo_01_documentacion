package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.univalle.fleetops.document.service.application.port.in.CancelDocumentCommand;
import co.edu.univalle.fleetops.document.service.application.port.in.CancelDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ComplianceUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.GetDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ListDocumentsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentUseCase;
import co.edu.univalle.fleetops.document.service.domain.exception.InvalidCancellationReasonException;
import co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException;
import co.edu.univalle.fleetops.document.service.domain.exception.ResourceNotFoundException;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@ActiveProfiles("test")
class DocumentCancellationControllerTest {

	private static final UUID DOCUMENT_ID = UUID.randomUUID();

	private static final UUID OWNER_ID = UUID.randomUUID();

	private static final UUID PREVIOUS_DOCUMENT_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-10-26T15:00:00Z");

	private static final Instant CANCELLED_AT = Instant.parse("2026-11-27T15:00:00Z");

	private static final String REASON = "Documento registrado por error";

	private static final String INVALID_REASON_MESSAGE =
			"El motivo de anulación es obligatorio y debe tener entre 10 y 200 caracteres";

	private static final String CANCELLATION_REQUEST = """
			{
				"reason": "Documento registrado por error"
			}
			""";

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
	void should_respond_200_when_document_is_cancelled() throws Exception {
		// Arrange
		CancelDocumentCommand command = new CancelDocumentCommand(DOCUMENT_ID, REASON);

		given(this.cancelDocument.cancel(command))
				.willReturn(cancelledView());

		// Act and Assert
		this.mockMvc.perform(post("/documents/{id}/cancellation", DOCUMENT_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CANCELLATION_REQUEST))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.document_id").value(DOCUMENT_ID.toString()))
				.andExpect(jsonPath("$.document_type").value("SOAT"))
				.andExpect(jsonPath("$.owner_type").value("VEHICLE"))
				.andExpect(jsonPath("$.owner_id").value(OWNER_ID.toString()))
				.andExpect(jsonPath("$.document_number").value("SOAT-12345"))
				.andExpect(jsonPath("$.issuing_entity").value("Aseguradora Ejemplo"))
				.andExpect(jsonPath("$.issue_date").value("2026-10-26"))
				.andExpect(jsonPath("$.expiration_date").value("2027-10-26"))
				.andExpect(jsonPath("$.license_category").value(nullValue()))
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.days_remaining").value(nullValue()))
				.andExpect(jsonPath("$.previous_document_id").value(PREVIOUS_DOCUMENT_ID.toString()))
				.andExpect(jsonPath("$.cancellation_reason").value(REASON))
				.andExpect(jsonPath("$.cancelled_at").value("2026-11-27T10:00:00-05:00"))
				.andExpect(jsonPath("$.created_at").value("2026-10-26T10:00:00-05:00"))
				.andExpect(jsonPath("$.updated_at").value("2026-11-27T10:00:00-05:00"));

		verify(this.cancelDocument).cancel(command);
		verifyNoInteractions(this.registerDocument, this.getDocument,
				this.compliance, this.listDocuments);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"uuid-invalido",
			"1-1-1-1-1",
			"550e8400-e29b-41d4-a716-44665544000",
			"550e8400-e29b-41d4-a716-44665544000g"
	})
	void should_respond_400_when_document_id_is_not_a_uuid(String documentId)
			throws Exception {

		this.mockMvc.perform(post("/documents/{id}/cancellation", documentId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CANCELLATION_REQUEST))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail")
						.value("El identificador debe ser un UUID válido"));

		verifyNoInteractions(this.cancelDocument);
	}

	@Test
	void should_respond_400_when_request_body_is_malformed() throws Exception {
		this.mockMvc.perform(post("/documents/{id}/cancellation", DOCUMENT_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(this.cancelDocument);
	}

	@Test
	void should_respond_404_when_document_does_not_exist() throws Exception {
		CancelDocumentCommand command = new CancelDocumentCommand(DOCUMENT_ID, REASON);

		given(this.cancelDocument.cancel(command))
				.willThrow(new ResourceNotFoundException("Documento no encontrado"));

		this.mockMvc.perform(post("/documents/{id}/cancellation", DOCUMENT_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CANCELLATION_REQUEST))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Documento no encontrado"));

		verify(this.cancelDocument).cancel(command);
	}

	@Test
	void should_respond_409_when_document_cannot_be_cancelled() throws Exception {
		CancelDocumentCommand command = new CancelDocumentCommand(DOCUMENT_ID, REASON);

		given(this.cancelDocument.cancel(command))
				.willThrow(new DomainConflictException(
						"El documento no se puede modificar en su estado actual"));

		this.mockMvc.perform(post("/documents/{id}/cancellation", DOCUMENT_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CANCELLATION_REQUEST))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail")
						.value("El documento no se puede modificar en su estado actual"));

		verify(this.cancelDocument).cancel(command);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "Error", "123456789" })
	void should_respond_422_when_cancellation_reason_is_invalid(String reason)
			throws Exception {

		CancelDocumentCommand command = new CancelDocumentCommand(DOCUMENT_ID, reason);

		given(this.cancelDocument.cancel(command))
				.willThrow(new InvalidCancellationReasonException());

		String request = """
				{
					"reason": "%s"
				}
				""".formatted(reason);

		this.mockMvc.perform(post("/documents/{id}/cancellation", DOCUMENT_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Datos inválidos"))
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").value(INVALID_REASON_MESSAGE))
				.andExpect(jsonPath("$.instance")
						.value("/documents/" + DOCUMENT_ID + "/cancellation"))
				.andExpect(jsonPath("$.detalles.length()").value(1))
				.andExpect(jsonPath("$.detalles[0].campo").value("reason"))
				.andExpect(jsonPath("$.detalles[0].mensaje")
						.value("Debe tener entre 10 y 200 caracteres"));

		verify(this.cancelDocument).cancel(command);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"reason\": null}", "null", "" })
	void should_respond_422_when_cancellation_reason_is_missing(String request)
			throws Exception {

		CancelDocumentCommand command = new CancelDocumentCommand(DOCUMENT_ID, null);

		given(this.cancelDocument.cancel(command))
				.willThrow(new InvalidCancellationReasonException());

		this.mockMvc.perform(post("/documents/{id}/cancellation", DOCUMENT_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Datos inválidos"))
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").value(INVALID_REASON_MESSAGE))
				.andExpect(jsonPath("$.instance")
						.value("/documents/" + DOCUMENT_ID + "/cancellation"))
				.andExpect(jsonPath("$.detalles.length()").value(1))
				.andExpect(jsonPath("$.detalles[0].campo").value("reason"))
				.andExpect(jsonPath("$.detalles[0].mensaje")
						.value("Debe tener entre 10 y 200 caracteres"));

		verify(this.cancelDocument).cancel(command);
	}

	@Test
	void should_respond_422_when_cancellation_reason_exceeds_maximum_length()
			throws Exception {

		String reason = "A".repeat(201);

		given(this.cancelDocument.cancel(any(CancelDocumentCommand.class)))
				.willThrow(new InvalidCancellationReasonException());

		String request = """
				{
					"reason": "%s"
				}
				""".formatted(reason);

		this.mockMvc.perform(post("/documents/{id}/cancellation", DOCUMENT_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Datos inválidos"))
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").value(INVALID_REASON_MESSAGE))
				.andExpect(jsonPath("$.instance")
						.value("/documents/" + DOCUMENT_ID + "/cancellation"))
				.andExpect(jsonPath("$.detalles.length()").value(1))
				.andExpect(jsonPath("$.detalles[0].campo").value("reason"))
				.andExpect(jsonPath("$.detalles[0].mensaje")
						.value("Debe tener entre 10 y 200 caracteres"));

		verify(this.cancelDocument).cancel(new CancelDocumentCommand(DOCUMENT_ID, reason));
	}

	private static DocumentView cancelledView() {
		return new DocumentView(
				DOCUMENT_ID,
				DocumentTypeCode.SOAT,
				OwnerType.VEHICLE,
				OWNER_ID,
				"SOAT-12345",
				"Aseguradora Ejemplo",
				LocalDate.of(2026, 10, 26),
				LocalDate.of(2027, 10, 26),
				null,
				DocumentStatus.CANCELLED,
				null,
				PREVIOUS_DOCUMENT_ID,
				REASON,
				CANCELLED_AT,
				CREATED,
				CANCELLED_AT);
	}

}
