package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentRegistration;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.LicenseCategory;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.model.ValidityPeriod;

/**
 * Mapper del agregado {@link Document} a su entidad JPA.
 */
final class DocumentPersistenceMapper {

	private DocumentPersistenceMapper() {
	}

	static DocumentJpaEntity toEntity(Document document) {
		String licenseCategory = (document.licenseCategory() != null) ? document.licenseCategory().value() : null;
		return new DocumentJpaEntity(document.id(), document.documentType().name(),
				document.owner().ownerType().name(), document.owner().ownerId(), document.documentNumber().value(),
				document.issuingEntity(), document.validity().issueDate(), document.validity().expirationDate(),
				document.status().name(), licenseCategory, document.createdAt(), document.updatedAt());
	}

	static Document toDomain(DocumentJpaEntity entity) {

		DocumentTypeCode documentType = DocumentTypeCode.valueOf(entity.getDocumentType());

		OwnerRef owner = new OwnerRef(
				OwnerType.valueOf(entity.getOwnerType()),
				entity.getOwnerId());

		DocumentNumber documentNumber = new DocumentNumber(entity.getDocumentNumber());

		ValidityPeriod validity = new ValidityPeriod(
				entity.getIssueDate(),
				entity.getExpirationDate());

		LicenseCategory licenseCategory =
				entity.getLicenseCategory() != null
						? new LicenseCategory(entity.getLicenseCategory())
						: null;

		DocumentRegistration registration = new DocumentRegistration(
				entity.getDocumentId(),
				owner,
				documentNumber,
				entity.getIssuingEntity(),
				validity,
				licenseCategory);

		DocumentStatus status = DocumentStatus.valueOf(entity.getStatus());

		return Document.reconstitute(
				registration,
				documentType,
				status,
				entity.getCreatedAt(),
				entity.getUpdatedAt(),
				entity.getPreviousDocumentId(),
				entity.getCancellationReason(),
				entity.getCancelledAt());
	}
}
