package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.service.StatusEvaluation;

/**
 * Read Model: un documento tal como lo ven los adaptadores de entrada, con el estado y los días restantes
 * calculados con la fecha de negocio (ADR-DOC-01).
 *
 * @param documentId identificador
 * @param documentType tipo de documento
 * @param ownerType tipo de propietario
 * @param ownerId propietario
 * @param documentNumber número completo; el adaptador decide si lo enmascara
 * @param issuingEntity entidad que lo expidió
 * @param issueDate fecha de inicio de vigencia
 * @param expirationDate fecha de vencimiento; vacía si no vence
 * @param licenseCategory categoría de la licencia; vacía en vehículos
 * @param status estado calculado
 * @param daysRemaining días restantes; vacío si no vence
 * @param createdAt creación, en UTC
 * @param updatedAt última modificación, en UTC
 */
public record DocumentView(UUID documentId, DocumentTypeCode documentType, OwnerType ownerType, UUID ownerId,
		String documentNumber, String issuingEntity, LocalDate issueDate, LocalDate expirationDate,
		String licenseCategory, DocumentStatus status, Integer daysRemaining, Instant createdAt, Instant updatedAt) {

	/**
	 * @param document documento del dominio
	 * @param evaluation estado calculado con la fecha de negocio
	 */
	public static DocumentView of(Document document, StatusEvaluation evaluation) {
		String licenseCategory = (document.licenseCategory() != null) ? document.licenseCategory().value() : null;
		return new DocumentView(document.id(), document.documentType(), document.owner().ownerType(),
				document.owner().ownerId(), document.documentNumber().value(), document.issuingEntity(),
				document.validity().issueDate(), document.validity().expirationDate(), licenseCategory,
				evaluation.status(), evaluation.daysRemaining(), document.createdAt(), document.updatedAt());
	}

}
