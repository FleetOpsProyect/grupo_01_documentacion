package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonView;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Respuesta JSON de GET /documents/expiring-soon (HU-DOC-08). Los campos sin valor se envían como null.
 */
public record ExpiringSoonResponse(int horizonDays, LocalDate evaluatedOn, List<ExpiringDocumentResponse> content,
		int page, int size, long totalElements, int totalPages) {

	static ExpiringSoonResponse of(ExpiringSoonView view) {
		List<ExpiringDocumentResponse> content = view.documents()
			.content()
			.stream()
			.map(document -> new ExpiringDocumentResponse(document.documentId(), document.documentType(),
					document.ownerType(), document.ownerId(), document.documentNumber(), document.licensePlate(),
					document.fullName(), document.expirationDate(), document.daysRemaining(), document.status()))
			.toList();
		return new ExpiringSoonResponse(view.horizonDays(), view.evaluatedOn(), content, view.documents().page(),
				view.documents().size(), view.documents().totalElements(), view.documents().totalPages());
	}

	public record ExpiringDocumentResponse(UUID documentId, DocumentTypeCode documentType, OwnerType ownerType,
			UUID ownerId, String documentNumber, String licensePlate, String fullName, LocalDate expirationDate,
			Integer daysRemaining, DocumentStatus status) {
	}

}
