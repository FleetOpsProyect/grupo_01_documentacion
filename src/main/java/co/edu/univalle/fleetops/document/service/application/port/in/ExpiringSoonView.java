package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Resultado de la consulta de documentos próximos a vencer (HU-DOC-08).
 *
 * @param horizonDays días consultados
 * @param evaluatedOn fecha de hoy (Bogotá) usada para calcular el estado y los días restantes
 * @param documents página de documentos, del que vence primero al que vence último
 */
public record ExpiringSoonView(int horizonDays, LocalDate evaluatedOn, PageResult<ExpiringDocumentView> documents) {

	/**
	 * Un documento próximo a vencer. La placa y el nombre vienen de las réplicas locales.
	 *
	 * @param licensePlate placa del vehículo; null en conductores o si la réplica no la tiene
	 * @param fullName nombre del conductor; null en vehículos o si la réplica no lo tiene
	 * @param daysRemaining días que faltan para el vencimiento
	 * @param status estado calculado con la fecha de hoy (ADR-DOC-01)
	 */
	public record ExpiringDocumentView(UUID documentId, DocumentTypeCode documentType, OwnerType ownerType,
			UUID ownerId, String documentNumber, String licensePlate, String fullName, LocalDate expirationDate,
			Integer daysRemaining, DocumentStatus status) {
	}

}
