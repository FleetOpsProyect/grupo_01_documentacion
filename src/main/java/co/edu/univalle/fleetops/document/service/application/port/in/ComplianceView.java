package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Read model de cumplimiento documental (HU-DOC-09).
 */
public record ComplianceView(OwnerType ownerType, UUID ownerId, LocalDate evaluatedOn, boolean compliant,
		List<ComplianceDocumentView> documents, List<DocumentTypeCode> missingDocuments) {

	public record ComplianceDocumentView(UUID documentId, DocumentTypeCode documentType, DocumentStatus status,
			LocalDate expirationDate, Integer daysRemaining) {
	}

}
