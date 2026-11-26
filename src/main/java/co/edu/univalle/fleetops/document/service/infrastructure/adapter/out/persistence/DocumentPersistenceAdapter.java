package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import co.edu.univalle.fleetops.document.service.application.port.in.PageQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.PageResult;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DocumentJpaRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DocumentJpaRepository.ExpiringDocumentRow;
import co.edu.univalle.fleetops.document.service.application.port.in.ListDocumentQuery;

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

	private static final List<String> NUMBER_RESERVING_STATUSES = Arrays.stream(DocumentStatus.values())
		.filter(DocumentStatus::reservesDocumentNumber)
		.map(Enum::name)
		.toList();

	private final DocumentJpaRepository repository;

	public DocumentPersistenceAdapter(DocumentJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public boolean existsNotCancelledByTypeAndNumber(DocumentTypeCode documentType, DocumentNumber documentNumber) {
		return this.repository.existsByDocumentTypeAndDocumentNumberAndStatusIn(documentType.name(),
				documentNumber.value(), NUMBER_RESERVING_STATUSES);
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

	@Override
	public Optional<Document> findById(UUID documentId) {
		return this.repository.findById(documentId)
				.map(DocumentPersistenceMapper::toDomain);
	}

	@Override
	public PageResult<Document> findDocuments(
			ListDocumentQuery query, LocalDate today, LocalDate alertHorizon) {

		PageQuery pageQuery = query.pageQuery();

		Page<DocumentJpaEntity> rows = this.repository.findDocuments(
				(query.ownerType() != null) ? query.ownerType().name() : null,
				query.ownerId(),
				(query.documentType() != null) ? query.documentType().name() : null,
				(query.status() != null) ? query.status().name() : null,
				query.expirationDateFrom(),
				query.expirationDateTo(),
				today,
				alertHorizon,
				PageRequest.of(pageQuery.page(), pageQuery.size()));

		List<Document> content = rows.getContent()
				.stream()
				.map(DocumentPersistenceMapper::toDomain)
				.toList();

		return new PageResult<>(content, pageQuery.page(), pageQuery.size(),
				rows.getTotalElements(), rows.getTotalPages());
	}

	@Override
	public List<ComplianceDocumentSnapshot> findComplianceDocuments(OwnerRef owner) {
		List<String> activeStatuses = Arrays.stream(DocumentStatus.values())
			.filter(DocumentStatus::isActive)
			.map(Enum::name)
			.toList();
		return this.repository.findByOwnerTypeAndOwnerIdAndStatusIn(owner.ownerType().name(),
				owner.ownerId(), activeStatuses)
			.stream()
			.map(entity -> new ComplianceDocumentSnapshot(entity.getDocumentId(),
					DocumentTypeCode.valueOf(entity.getDocumentType()), entity.getExpirationDate()))
			.toList();
	}

	@Override
	public List<UUID> findExpirationCandidateIds(LocalDate today, LocalDate alertHorizon) {
		return this.repository.findExpirationCandidateIds(today, alertHorizon);
	}

	@Override
	public Optional<ExpirationCandidate> lockExpirationCandidate(UUID documentId) {
		return this.repository.findForUpdate(documentId)
			.filter(DocumentPersistenceAdapter::isExpirationCandidate)
			.map(DocumentPersistenceAdapter::toExpirationCandidate);
	}

	@Override
	public void updateStatus(UUID documentId, DocumentStatus status, Instant updatedAt) {
		DocumentJpaEntity entity = this.repository.findById(documentId)
			.orElseThrow(() -> new IllegalStateException("El documento " + documentId + " no existe"));
		entity.changeStatus(status.name(), updatedAt);
	}

	@Override
	public PageResult<ExpiringDocumentSnapshot> findExpiringBetween(LocalDate from, LocalDate to,
			DocumentTypeCode documentType, OwnerType ownerType, PageQuery pageQuery) {
		Page<ExpiringDocumentRow> rows = this.repository.findExpiringBetween(ACTIVE_STATUSES, from, to,
				(documentType != null) ? documentType.name() : null, (ownerType != null) ? ownerType.name() : null,
				PageRequest.of(pageQuery.page(), pageQuery.size()));
		List<ExpiringDocumentSnapshot> content = rows.getContent()
			.stream()
			.map(DocumentPersistenceAdapter::toExpiringSnapshot)
			.toList();
		return new PageResult<>(content, pageQuery.page(), pageQuery.size(), rows.getTotalElements(),
				rows.getTotalPages());
	}

	private static ExpiringDocumentSnapshot toExpiringSnapshot(ExpiringDocumentRow row) {
		return new ExpiringDocumentSnapshot(row.getDocumentId(), DocumentTypeCode.valueOf(row.getDocumentType()),
				OwnerType.valueOf(row.getOwnerType()), row.getOwnerId(), row.getDocumentNumber(),
				row.getExpirationDate(), row.getLicensePlate(), row.getFullName());
	}

	private static boolean isExpirationCandidate(DocumentJpaEntity entity) {
		DocumentStatus status = DocumentStatus.valueOf(entity.getStatus());
		return entity.getExpirationDate() != null
				&& (status == DocumentStatus.VALID || status == DocumentStatus.EXPIRING_SOON);
	}

	private static ExpirationCandidate toExpirationCandidate(DocumentJpaEntity entity) {
		return new ExpirationCandidate(entity.getDocumentId(), DocumentTypeCode.valueOf(entity.getDocumentType()),
				new OwnerRef(OwnerType.valueOf(entity.getOwnerType()), entity.getOwnerId()),
				entity.getExpirationDate(), DocumentStatus.valueOf(entity.getStatus()));
	}

	private static RuntimeException translate(DataIntegrityViolationException ex, Document document) {
		String constraint = ConstraintViolations.constraintName(ex);
		if (UK_TYPE_NUMBER.equals(constraint)) {
			return new DomainConflictException(
					"Ya existe un documento " + document.documentType() + " no anulado con ese document_number");
		}
		if (UK_ONE_ACTIVE_PER_OWNER_TYPE.equals(constraint)) {
			return Document.activeDocumentConflict(document.documentType());
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
