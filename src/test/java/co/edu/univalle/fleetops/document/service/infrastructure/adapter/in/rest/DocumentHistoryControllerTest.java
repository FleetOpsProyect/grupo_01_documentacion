package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.GetDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentUseCase;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@ActiveProfiles("test")
class DocumentHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetDocumentUseCase getDocument;

    @MockitoBean
    private RegisterDocumentUseCase registerDocument;

    @Test
    void should_return_historical_fields_when_document_is_cancelled()
            throws Exception {

        // Arrange
        UUID documentId = UUID.randomUUID();
        UUID previousDocumentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        DocumentView view = new DocumentView(
                documentId,
                DocumentTypeCode.SOAT,
                OwnerType.VEHICLE,
                ownerId,
                "SOAT-12345",
                "Aseguradora Ejemplo",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                null,
                DocumentStatus.CANCELLED,
                null,
                previousDocumentId,
                "Documento anulado",
                Instant.parse("2026-11-20T15:00:00Z"),
                Instant.parse("2026-01-01T15:00:00Z"),
                Instant.parse("2026-11-20T15:00:00Z"));

        given(this.getDocument.getById(documentId))
                .willReturn(view);

        // Act + Assert
        this.mockMvc.perform(get("/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_id")
                        .value(documentId.toString()))
                .andExpect(jsonPath("$.status")
                        .value("CANCELLED"))
                .andExpect(jsonPath("$.days_remaining")
                        .value(nullValue()))
                .andExpect(jsonPath("$.previous_document_id")
                        .value(previousDocumentId.toString()))
                .andExpect(jsonPath("$.cancellation_reason")
                        .value("Documento anulado"))
                .andExpect(jsonPath("$.cancelled_at")
                        .value("2026-11-20T10:00:00-05:00"))
                .andExpect(jsonPath("$.created_at")
                        .value("2026-01-01T10:00:00-05:00"))
                .andExpect(jsonPath("$.updated_at")
                        .value("2026-11-20T10:00:00-05:00"));

        verify(this.getDocument).getById(documentId);
        verifyNoInteractions(this.registerDocument);
    }
}
