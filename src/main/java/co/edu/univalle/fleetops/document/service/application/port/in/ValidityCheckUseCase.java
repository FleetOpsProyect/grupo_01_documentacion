package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Puerto de entrada: vehículos o conductores con toda su documentación obligatoria vigente hasta una fecha
 * (HU-DOC-12, GET /documents/validity-check). Solo lectura: no modifica datos ni publica eventos (criterio 5).
 */
public interface ValidityCheckUseCase {

	/**
	 * @throws co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException si
	 * valid_until es anterior a la fecha actual (422, criterio 4)
	 */
	ValidityCheckPage check(ValidityCheckQuery query);

	/**
	 * Parámetros de la consulta, validados al construirse (criterio 4: 400).
	 *
	 * @param ownerType VEHICLE o DRIVER
	 * @param validUntil fecha hasta la que deben estar vigentes los documentos obligatorios
	 * @param ownerIds propietarios a evaluar, hasta 100; vacío para evaluar todos los activos de la réplica
	 * @param page página desde 0
	 * @param size tamaño de página, de 1 a 100
	 */
	record ValidityCheckQuery(OwnerType ownerType, LocalDate validUntil, Set<UUID> ownerIds, int page, int size) {

		/**
		 * Máximo de owner_ids por consulta.
		 */
		public static final int MAX_OWNER_IDS = 100;

		/**
		 * Tamaño máximo de página (DAS sec. 7).
		 */
		public static final int MAX_SIZE = 100;

		public static final String OWNER_TYPE_MESSAGE =
				"El parámetro 'owner_type' es obligatorio y debe ser VEHICLE o DRIVER.";

		public static final String VALID_UNTIL_MESSAGE =
				"El parámetro 'valid_until' es obligatorio y debe tener formato YYYY-MM-DD.";

		public static final String OWNER_IDS_LIMIT_MESSAGE =
				"El parámetro 'owner_ids' admite como máximo " + MAX_OWNER_IDS + " UUID.";

		public static final String PAGE_MESSAGE = "El parámetro 'page' debe ser un entero mayor o igual a 0.";

		public static final String SIZE_MESSAGE = "El parámetro 'size' debe ser un entero entre 1 y " + MAX_SIZE + ".";

		public ValidityCheckQuery {
			if (ownerType == null) {
				throw new InvalidQueryParameterException(OWNER_TYPE_MESSAGE);
			}
			if (validUntil == null) {
				throw new InvalidQueryParameterException(VALID_UNTIL_MESSAGE);
			}
			ownerIds = Set.copyOf(Objects.requireNonNull(ownerIds, "ownerIds"));
			if (ownerIds.size() > MAX_OWNER_IDS) {
				throw new InvalidQueryParameterException(OWNER_IDS_LIMIT_MESSAGE);
			}
			if (page < 0) {
				throw new InvalidQueryParameterException(PAGE_MESSAGE);
			}
			if (size < 1 || size > MAX_SIZE) {
				throw new InvalidQueryParameterException(SIZE_MESSAGE);
			}
		}

	}

	/**
	 * Página de propietarios aptos (criterio 3). Los no aptos no aparecen.
	 *
	 * @param page página pedida
	 * @param size tamaño de página pedido
	 * @param totalElements propietarios aptos en total
	 * @param totalPages páginas en total
	 * @param data propietarios aptos de la página
	 */
	record ValidityCheckPage(int page, int size, long totalElements, int totalPages, List<EligibleOwnerView> data) {

		public ValidityCheckPage {
			data = List.copyOf(Objects.requireNonNull(data, "data"));
		}

	}

	/**
	 * Propietario apto.
	 *
	 * @param ownerId id del vehículo o conductor
	 * @param licensePlate placa (solo vehículos)
	 * @param fullName nombre (solo conductores)
	 * @param documents documentos obligatorios vigentes, en el orden del catálogo
	 */
	record EligibleOwnerView(UUID ownerId, String licensePlate, String fullName, List<ValidDocumentView> documents) {

		public EligibleOwnerView {
			Objects.requireNonNull(ownerId, "ownerId");
			documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
		}

	}

	/**
	 * Documento obligatorio vigente.
	 *
	 * @param documentType tipo
	 * @param expirationDate vencimiento; vacío en PROPERTY_CARD, que no vence
	 */
	record ValidDocumentView(DocumentTypeCode documentType, LocalDate expirationDate) {

		public ValidDocumentView {
			Objects.requireNonNull(documentType, "documentType");
		}

	}

}
