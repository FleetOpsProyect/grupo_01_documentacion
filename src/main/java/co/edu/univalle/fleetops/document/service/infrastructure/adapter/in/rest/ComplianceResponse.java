package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

public record ComplianceResponse(OwnerType ownerType, UUID ownerId, LocalDate evaluatedOn, boolean compliant,
		List<ComplianceDocumentResponse> documents, List<DocumentTypeCode> missingDocuments) {

	public record ComplianceDocumentResponse(UUID documentId, DocumentTypeCode documentType, DocumentStatus status,
			LocalDate expirationDate, Integer daysRemaining) {
	}

}
