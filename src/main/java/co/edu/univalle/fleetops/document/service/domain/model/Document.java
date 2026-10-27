package co.edu.univalle.fleetops.document.service.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpired;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentRegistered;
import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;
import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;

/**
 * Aggregate Root: documento legal de un vehículo o un conductor (DAS sec. 6). Es la única entidad de negocio del
 * servicio.
 *
 * <p>El registro valida las reglas que dependen del tipo (ADR-DOC-10) y calcula el estado con la regla única
 * (ADR-DOC-01). Las reglas que necesitan consultar datos externos al agregado, como la unicidad o la existencia
 * del propietario en la réplica, las valida el servicio de aplicación.
 */
public final class Document {

	static final int ISSUING_ENTITY_MAX_LENGTH = 150;

	private final UUID id;

	private final DocumentTypeCode documentType;

	private final OwnerRef owner;

	private final DocumentNumber documentNumber;

	private final String issuingEntity;

	private final ValidityPeriod validity;

	private final LicenseCategory licenseCategory;

	private final DocumentStatus status;

	private final Instant createdAt;

	private final Instant updatedAt;

	private final List<DomainEvent> domainEvents = new ArrayList<>();

	private Document(DocumentRegistration registration, DocumentTypeCode documentType, String issuingEntity,
			DocumentStatus status, Instant now) {
		this.id = registration.documentId();
		this.documentType = documentType;
		this.owner = registration.owner();
		this.documentNumber = registration.documentNumber();
		this.issuingEntity = issuingEntity;
		this.validity = registration.validity();
		this.licenseCategory = registration.licenseCategory();
		this.status = status;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * Registra un documento nuevo (HU-DOC-01 y HU-DOC-02) y deja pendientes sus eventos de dominio:
	 * {@link DocumentRegistered} siempre y {@link DocumentExpired} si nace vencido.
	 * @param registration datos del documento
	 * @param rules reglas del tipo leídas del catálogo
	 * @param calculator regla única de estado
	 * @param today fecha de negocio
	 * @param now instante actual en UTC
	 * @throws BusinessRuleViolationException si los datos no cumplen las reglas del tipo
	 */
	public static Document register(DocumentRegistration registration, DocumentTypeRules rules,
			DocumentStatusCalculator calculator, LocalDate today, Instant now) {
		Objects.requireNonNull(registration, "registration");
		Objects.requireNonNull(rules, "rules");
		Objects.requireNonNull(calculator, "calculator");
		Objects.requireNonNull(now, "now");
		checkTypeIsActive(rules);
		checkOwnerMatchesType(rules, registration.owner());
		checkExpirationMatchesType(rules, registration.validity());
		checkLicenseCategory(rules, registration.owner(), registration.licenseCategory());
		String issuingEntity = requireIssuingEntity(registration.issuingEntity());

		DocumentStatus status = calculator.evaluate(registration.validity().expirationDate(), today).status();
		Document document = new Document(registration, rules.code(), issuingEntity, status, now);
		document.domainEvents.add(new DocumentRegistered(document.id, document.documentType, document.owner,
				document.issuingEntity, document.validity, document.licenseCategory, status, now));
		if (status == DocumentStatus.EXPIRED) {
			document.domainEvents.add(new DocumentExpired(document.id, document.documentType, document.owner,
					document.validity.expirationDate(), now));
		}
		return document;
	}

	private static void checkTypeIsActive(DocumentTypeRules rules) {
		if (!rules.active()) {
			throw new BusinessRuleViolationException("El tipo de documento " + rules.code() + " no está habilitado");
		}
	}

	private static void checkOwnerMatchesType(DocumentTypeRules rules, OwnerRef owner) {
		if (rules.ownerType() != owner.ownerType()) {
			throw new BusinessRuleViolationException("El tipo de documento " + rules.code()
					+ " solo se registra para un propietario " + rules.ownerType());
		}
	}

	private static void checkExpirationMatchesType(DocumentTypeRules rules, ValidityPeriod validity) {
		if (rules.hasExpiration() && !validity.hasExpiration()) {
			throw new BusinessRuleViolationException("expiration_date es obligatoria para el tipo " + rules.code());
		}
		if (!rules.hasExpiration() && validity.hasExpiration()) {
			throw new BusinessRuleViolationException(
					"El tipo " + rules.code() + " no tiene vencimiento: expiration_date debe ir vacía");
		}
	}

	private static void checkLicenseCategory(DocumentTypeRules rules, OwnerRef owner,
			LicenseCategory licenseCategory) {
		if (owner.ownerType() == OwnerType.DRIVER && licenseCategory == null) {
			throw new BusinessRuleViolationException("license_category es obligatoria para el tipo " + rules.code());
		}
		if (owner.ownerType() == OwnerType.VEHICLE && licenseCategory != null) {
			throw new BusinessRuleViolationException(
					"license_category solo aplica a la licencia de conducción, no al tipo " + rules.code());
		}
	}

	private static String requireIssuingEntity(String issuingEntity) {
		if (issuingEntity == null || issuingEntity.isBlank()) {
			throw new BusinessRuleViolationException("issuing_entity es obligatoria");
		}
		String value = issuingEntity.strip();
		if (value.length() > ISSUING_ENTITY_MAX_LENGTH) {
			throw new BusinessRuleViolationException(
					"issuing_entity no puede tener más de " + ISSUING_ENTITY_MAX_LENGTH + " caracteres");
		}
		return value;
	}

	/**
	 * Entrega los eventos pendientes y los descarta del agregado, para que se registren una sola vez en el outbox.
	 */
	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = List.copyOf(this.domainEvents);
		this.domainEvents.clear();
		return events;
	}

	public UUID id() {
		return this.id;
	}

	public DocumentTypeCode documentType() {
		return this.documentType;
	}

	public OwnerRef owner() {
		return this.owner;
	}

	public DocumentNumber documentNumber() {
		return this.documentNumber;
	}

	public String issuingEntity() {
		return this.issuingEntity;
	}

	public ValidityPeriod validity() {
		return this.validity;
	}

	/**
	 * Categoría de la licencia; vacía en los documentos de vehículo.
	 */
	public LicenseCategory licenseCategory() {
		return this.licenseCategory;
	}

	/**
	 * Estado guardado al registrar. Las consultas lo recalculan con la fecha actual (ADR-DOC-01).
	 */
	public DocumentStatus status() {
		return this.status;
	}

	public Instant createdAt() {
		return this.createdAt;
	}

	public Instant updatedAt() {
		return this.updatedAt;
	}

}
