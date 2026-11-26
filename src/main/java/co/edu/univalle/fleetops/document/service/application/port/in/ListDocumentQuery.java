package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Parámetros de la consulta de documentos (HU-DOC-03).
 *
 * @param ownerType filtro por tipo de propietario
 * @param ownerId identificador del propietario
 * @param documentType filtro por tipo de documento
 * @param status filtro por estado del documento
 * @param expirationDateFrom fecha mínima de vencimiento, inclusiva
 * @param expirationDateTo fecha máxima de vencimiento, inclusiva
 * @param pageQuery página solicitada
 */
public record ListDocumentQuery(
		OwnerType ownerType,
		UUID ownerId,
		DocumentTypeCode documentType,
		DocumentStatus status,
		LocalDate expirationDateFrom,
		LocalDate expirationDateTo,
		PageQuery pageQuery) {

	public ListDocumentQuery {
		if (ownerId != null && ownerType == null) {
			throw InvalidQueryParameterException.invalidFilter("owner_type");
		}

		if (expirationDateFrom != null && expirationDateTo != null
				&& expirationDateFrom.isAfter(expirationDateTo)) {
			throw InvalidQueryParameterException.invalidFilter("expiration_date_from");
		}

		Objects.requireNonNull(pageQuery, "pageQuery");
	}

}
