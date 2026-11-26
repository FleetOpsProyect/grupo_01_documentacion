package co.edu.univalle.fleetops.document.service.application.port.in;

import java.util.Objects;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Parámetros de la consulta de documentos próximos a vencer (HU-DOC-08).
 *
 * @param days cuántos días hacia adelante se buscan, de 1 a 365
 * @param documentType filtro por tipo de documento; null si no se filtra. PROPERTY_CARD no se permite porque no vence
 * @param ownerType filtro por tipo de propietario; null si no se filtra
 * @param pageQuery página solicitada
 */
public record ExpiringSoonQuery(int days, DocumentTypeCode documentType, OwnerType ownerType,
		PageQuery pageQuery) {

	public static final int DEFAULT_DAYS = 30;

	public static final int MAX_DAYS = 365;

	/** Mensaje definido en HU-DOC-08, criterio 1. */
	public static final String INVALID_DAYS = "El parámetro days debe ser un entero entre 1 y 365";

	public ExpiringSoonQuery {
		if (days < 1 || days > MAX_DAYS) {
			throw new InvalidQueryParameterException(INVALID_DAYS);
		}
		if (documentType == DocumentTypeCode.PROPERTY_CARD) {
			throw InvalidQueryParameterException.invalidFilter("document_type");
		}
		Objects.requireNonNull(pageQuery, "pageQuery");
	}

}
