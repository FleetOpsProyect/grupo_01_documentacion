package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentCommand;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Mapper entre los DTO REST y el puerto de entrada.
 */
final class DocumentRestMapper {

	private DocumentRestMapper() {
	}

	/**
	 * @param request cuerpo ya validado con Bean Validation: document_type y owner_type tienen valores del catálogo
	 */
	static RegisterDocumentCommand toCommand(RegisterDocumentRequest request) {
		return new RegisterDocumentCommand(DocumentTypeCode.valueOf(request.documentType()),
				OwnerType.valueOf(request.ownerType()), request.ownerId(), request.documentNumber(),
				request.issuingEntity(), request.issueDate(), request.expirationDate(), request.licenseCategory());
	}

	static DocumentResponse toResponse(DocumentView view) {
		return new DocumentResponse(view.documentId(), view.documentType(), view.ownerType(), view.ownerId(),
				view.documentNumber(), view.issuingEntity(), view.issueDate(), view.expirationDate(),
				view.licenseCategory(), view.status(), view.daysRemaining(), null, null, null, view.createdAt(),
				view.updatedAt());
	}

}
