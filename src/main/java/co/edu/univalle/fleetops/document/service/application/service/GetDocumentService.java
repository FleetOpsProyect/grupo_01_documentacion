package co.edu.univalle.fleetops.document.service.application.service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.GetDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.domain.exception.ResourceNotFoundException;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;
import co.edu.univalle.fleetops.document.service.domain.service.StatusEvaluation;

/**
 * Servicio de aplicación para consultar documentos.
 * HU-DOC-03.
 */
public final class GetDocumentService implements GetDocumentUseCase {

    private final DocumentRepository documents;

    private final BusinessClock clock;

    private final DocumentStatusCalculator statusCalculator;

    public GetDocumentService(
            DocumentRepository documents,
            BusinessClock clock,
            DocumentStatusCalculator statusCalculator) {

        this.documents = Objects.requireNonNull(documents, "documents");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.statusCalculator = Objects.requireNonNull(
                statusCalculator, "statusCalculator");
    }

    @Override
    public DocumentView getById(UUID documentId) {

        Objects.requireNonNull(documentId, "documentId");

        Document document = this.documents.findById(documentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Documento no encontrado"));

        LocalDate today = this.clock.today();

        StatusEvaluation evaluation;

        if (document.status() == DocumentStatus.REPLACED
                || document.status() == DocumentStatus.CANCELLED) {

            evaluation = new StatusEvaluation(
                    document.status(), null);

        } else {

            evaluation = this.statusCalculator.evaluate(
                    document.validity().expirationDate(),
                    today);
        }

        return DocumentView.of(document, evaluation);
    }

}