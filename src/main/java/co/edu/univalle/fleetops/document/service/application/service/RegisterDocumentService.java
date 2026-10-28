package co.edu.univalle.fleetops.document.service.application.service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentCommand;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.out.AuditLogRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.CorrelationIdProvider;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentTypeRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OwnerReplicaRepository;
import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;
import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;
import co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentRegistration;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeRules;
import co.edu.univalle.fleetops.document.service.domain.model.LicenseCategory;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.ValidityPeriod;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;

/**
 * Application Service: registra un documento (HU-DOC-01 y HU-DOC-02).
 *
 * <p>Orden: reglas del tipo en el agregado, propietario activo en la réplica (422), unicidad del número y de un
 * documento activo por tipo (409), y después persistencia del documento, la auditoría y sus eventos en el outbox.
 * La transacción la aplica un decorador de infraestructura, para que esta clase no dependa de Spring.
 */
public final class RegisterDocumentService implements RegisterDocumentUseCase {

	private final DocumentTypeRepository documentTypes;

	private final OwnerReplicaRepository owners;

	private final DocumentRepository documents;

	private final AuditLogRepository auditLog;

	private final OutboxRepository outbox;

	private final IdGenerator idGenerator;

	private final CorrelationIdProvider correlationIds;

	private final BusinessClock clock;

	private final DocumentStatusCalculator statusCalculator;

	public RegisterDocumentService(DocumentTypeRepository documentTypes, OwnerReplicaRepository owners,
			DocumentRepository documents, AuditLogRepository auditLog, OutboxRepository outbox,
			IdGenerator idGenerator, CorrelationIdProvider correlationIds, BusinessClock clock,
			DocumentStatusCalculator statusCalculator) {
		this.documentTypes = Objects.requireNonNull(documentTypes, "documentTypes");
		this.owners = Objects.requireNonNull(owners, "owners");
		this.documents = Objects.requireNonNull(documents, "documents");
		this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
		this.outbox = Objects.requireNonNull(outbox, "outbox");
		this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
		this.correlationIds = Objects.requireNonNull(correlationIds, "correlationIds");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.statusCalculator = Objects.requireNonNull(statusCalculator, "statusCalculator");
	}

	@Override
	public DocumentView register(RegisterDocumentCommand command) {
		Objects.requireNonNull(command, "command");
		DocumentTypeRules rules = this.documentTypes.findByCode(command.documentType())
			.orElseThrow(() -> new BusinessRuleViolationException(
					"El tipo de documento " + command.documentType() + " no existe en el catálogo"));
		OwnerRef owner = new OwnerRef(command.ownerType(), command.ownerId());
		DocumentRegistration registration = new DocumentRegistration(this.idGenerator.newId(), owner,
				new DocumentNumber(command.documentNumber()), command.issuingEntity(),
				new ValidityPeriod(command.issueDate(), command.expirationDate()),
				licenseCategoryOf(command.licenseCategory()));
		LocalDate today = this.clock.today();
		Document document = Document.register(registration, rules, this.statusCalculator, today, this.clock.now());

		checkOwnerIsActive(owner);
		checkUniqueness(document);

		this.documents.save(document);
		UUID correlationId = this.correlationIds.currentCorrelationId().orElse(null);
		this.auditLog.recordRegistration(document, correlationId);
		for (DomainEvent event : document.pullDomainEvents()) {
			this.outbox.append(this.idGenerator.newId(), event, correlationId);
		}
		return DocumentView.of(document,
				this.statusCalculator.evaluate(document.validity().expirationDate(), today));
	}

	private void checkOwnerIsActive(OwnerRef owner) {
		if (!this.owners.isActiveOwner(owner)) {
			throw new BusinessRuleViolationException("El propietario " + owner.ownerType() + " " + owner.ownerId()
					+ " no existe o no está activo en la réplica local");
		}
	}

	private void checkUniqueness(Document document) {
		if (this.documents.existsNotCancelledByTypeAndNumber(document.documentType(), document.documentNumber())) {
			throw new DomainConflictException(
					"Ya existe un documento " + document.documentType() + " no anulado con ese document_number");
		}
		if (this.documents.existsActiveByOwnerAndType(document.owner(), document.documentType())) {
			throw new DomainConflictException("El propietario ya tiene un documento " + document.documentType()
					+ " activo. Para cambiarlo use la renovación");
		}
	}

	private static LicenseCategory licenseCategoryOf(String value) {
		return (value == null || value.isBlank()) ? null : new LicenseCategory(value);
	}

}
