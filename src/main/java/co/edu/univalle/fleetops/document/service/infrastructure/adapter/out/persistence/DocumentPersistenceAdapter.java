package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.util.Arrays;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DocumentJpaRepository;

/**
 * Adaptador de {@link DocumentRepository} sobre JPA.
 *
 * <p>Si dos peticiones simultáneas pasan las validaciones de la aplicación, la base de datos rechaza a una por sus
 * restricciones únicas. Aquí esa violación se traduce al mismo 409 que da la validación previa.
 */
@Component
public class DocumentPersistenceAdapter implements DocumentRepository {

	static final String UK_TYPE_NUMBER = "uk_documents_type_number";

	static final String UK_ONE_ACTIVE_PER_OWNER_TYPE = "uk_documents_one_active_per_owner_type";

	private static final List<String> ACTIVE_STATUSES = Arrays.stream(DocumentStatus.values())
		.filter(DocumentStatus::isActive)
		.map(Enum::name)
		.toList();

	private final DocumentJpaRepository repository;

	public DocumentPersistenceAdapter(DocumentJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public boolean existsByTypeAndNumber(DocumentTypeCode documentType, DocumentNumber documentNumber) {
		return this.repository.existsByDocumentTypeAndDocumentNumber(documentType.name(), documentNumber.value());
	}

	@Override
	public boolean existsActiveByOwnerAndType(OwnerRef owner, DocumentTypeCode documentType) {
		return this.repository.existsByOwnerTypeAndOwnerIdAndDocumentTypeAndStatusIn(owner.ownerType().name(),
				owner.ownerId(), documentType.name(), ACTIVE_STATUSES);
	}

	@Override
	public void save(Document document) {
		try {
			this.repository.saveAndFlush(DocumentPersistenceMapper.toEntity(document));
		}
		catch (DataIntegrityViolationException ex) {
			throw translate(ex, document);
		}
	}

	private static RuntimeException translate(DataIntegrityViolationException ex, Document document) {
		String constraint = constraintName(ex);
		if (UK_TYPE_NUMBER.equals(constraint)) {
			return new DomainConflictException(
					"Ya existe un documento " + document.documentType() + " con ese document_number");
		}
		if (UK_ONE_ACTIVE_PER_OWNER_TYPE.equals(constraint)) {
			return new DomainConflictException("El propietario ya tiene un documento " + document.documentType()
					+ " activo. Para cambiarlo use la renovación");
		}
		return ex;
	}

	private static String constraintName(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation.getConstraintName();
			}
		}
		return null;
	}

}
