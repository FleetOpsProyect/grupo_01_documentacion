package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;

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

}
