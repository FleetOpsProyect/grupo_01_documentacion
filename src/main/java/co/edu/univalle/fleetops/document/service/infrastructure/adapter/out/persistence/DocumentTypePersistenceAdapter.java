package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import co.edu.univalle.fleetops.document.service.application.port.out.DocumentTypeRepository;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeRules;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentTypeJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DocumentTypeJpaRepository;

/**
 * Adaptador de {@link DocumentTypeRepository}: lee el catálogo {@code document_types} (ADR-DOC-10).
 */
@Component
public class DocumentTypePersistenceAdapter implements DocumentTypeRepository {

	private final DocumentTypeJpaRepository repository;

	public DocumentTypePersistenceAdapter(DocumentTypeJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<DocumentTypeRules> findByCode(DocumentTypeCode code) {
		return this.repository.findById(code.name()).map(DocumentTypePersistenceAdapter::toRules);
	}

	private static DocumentTypeRules toRules(DocumentTypeJpaEntity entity) {
		return new DocumentTypeRules(DocumentTypeCode.valueOf(entity.getCode()),
				OwnerType.valueOf(entity.getOwnerType()), entity.hasExpiration(), entity.isMandatory(),
				entity.isRenewable(), entity.isActive());
	}

}
