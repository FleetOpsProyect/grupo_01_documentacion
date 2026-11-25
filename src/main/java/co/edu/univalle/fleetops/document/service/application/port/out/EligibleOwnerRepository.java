package co.edu.univalle.fleetops.document.service.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Puerto de salida: propietarios aptos para operar hasta una fecha (HU-DOC-12, DAS sec. 6.3).
 *
 * <p>Un propietario es apto si está activo en la réplica y, para cada tipo obligatorio del catálogo
 * {@code document_types} (ADR-DOC-10), tiene un documento activo con expiration_date igual o posterior a
 * {@code validUntil}; en los tipos sin vencimiento (PROPERTY_CARD) basta con el documento activo. La regla se evalúa en
 * la base de datos para paginar sobre los aptos sin cargar a todos los propietarios.
 */
public interface EligibleOwnerRepository {

	/**
	 * @param ownerIds propietarios a evaluar; vacío para todos los activos del tipo
	 * @param offset primer resultado (page × size)
	 * @param limit máximo de resultados
	 */
	EligibleOwners findEligible(OwnerType ownerType, LocalDate validUntil, Set<UUID> ownerIds, long offset,
			int limit);

	/**
	 * @param total aptos en total
	 * @param owners aptos de la página, ordenados por placa o nombre y después por id
	 */
	record EligibleOwners(long total, List<EligibleOwner> owners) {

		public EligibleOwners {
			owners = List.copyOf(Objects.requireNonNull(owners, "owners"));
		}

	}

	/**
	 * @param ownerId id del propietario
	 * @param label placa del vehículo o nombre del conductor
	 * @param documents documentos obligatorios vigentes
	 */
	record EligibleOwner(UUID ownerId, String label, List<ValidDocument> documents) {

		public EligibleOwner {
			Objects.requireNonNull(ownerId, "ownerId");
			documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
		}

	}

	/**
	 * @param expirationDate vacío en los tipos que no vencen
	 */
	record ValidDocument(DocumentTypeCode documentType, LocalDate expirationDate) {

		public ValidDocument {
			Objects.requireNonNull(documentType, "documentType");
		}

	}

}
