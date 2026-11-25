package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;

/**
 * Respuesta de GET /documents/validity-check (HU-DOC-12, criterio 3): paginación y {@code data} con los propietarios
 * aptos (Excel de endpoints, O-16). Jackson la escribe en snake_case; {@code license_plate} y {@code full_name} salen
 * siempre, uno de ellos en null.
 */
public record ValidityCheckResponse(int page, int size, long totalElements, int totalPages,
		List<EligibleOwnerResponse> data) {

	/**
	 * @param licensePlate placa (vehículos); null en conductores
	 * @param fullName nombre (conductores); null en vehículos
	 */
	public record EligibleOwnerResponse(UUID ownerId, String licensePlate, String fullName,
			List<ValidDocumentResponse> documents) {
	}

	/**
	 * @param expirationDate null en PROPERTY_CARD, que no vence
	 */
	public record ValidDocumentResponse(DocumentTypeCode documentType, LocalDate expirationDate) {
	}

}
