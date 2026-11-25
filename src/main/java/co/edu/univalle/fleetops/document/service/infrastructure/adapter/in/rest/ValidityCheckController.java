package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.univalle.fleetops.document.service.application.port.in.InvalidQueryParameterException;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase.ValidityCheckPage;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase.ValidityCheckQuery;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest.ValidityCheckResponse.EligibleOwnerResponse;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest.ValidityCheckResponse.ValidDocumentResponse;

/**
 * GET /documents/validity-check (HU-DOC-12, DAS sec. 7): vehículos o conductores con toda su documentación
 * obligatoria vigente hasta una fecha.
 *
 * <p>Los parámetros se reciben como texto para responder 400 con un mensaje que nombra el parámetro del contrato
 * (criterio 4). owner_ids es una lista separada por comas (Excel de endpoints, «Listas en query»). Solo lectura: no
 * modifica datos ni publica eventos (criterio 5).
 */
@RestController
@RequestMapping("/documents")
public class ValidityCheckController {

	static final String DEFAULT_PAGE = "0";

	static final String DEFAULT_SIZE = "20";

	private final ValidityCheckUseCase validityCheck;

	public ValidityCheckController(ValidityCheckUseCase validityCheck) {
		this.validityCheck = validityCheck;
	}

	@GetMapping("/validity-check")
	public ValidityCheckResponse validityCheck(@RequestParam(name = "owner_type", required = false) String ownerType,
			@RequestParam(name = "valid_until", required = false) String validUntil,
			@RequestParam(name = "owner_ids", required = false) String ownerIds,
			@RequestParam(name = "page", defaultValue = DEFAULT_PAGE) String page,
			@RequestParam(name = "size", defaultValue = DEFAULT_SIZE) String size) {
		ValidityCheckQuery query = new ValidityCheckQuery(parseOwnerType(ownerType), parseDate(validUntil),
				parseOwnerIds(ownerIds), parseInt(page, ValidityCheckQuery.PAGE_MESSAGE),
				parseInt(size, ValidityCheckQuery.SIZE_MESSAGE));
		return toResponse(this.validityCheck.check(query));
	}

	private static OwnerType parseOwnerType(String value) {
		if (value == null) {
			return null;
		}
		try {
			return OwnerType.valueOf(value.strip());
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidQueryParameterException(ValidityCheckQuery.OWNER_TYPE_MESSAGE);
		}
	}

	private static LocalDate parseDate(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value.strip());
		}
		catch (DateTimeParseException ex) {
			throw new InvalidQueryParameterException(ValidityCheckQuery.VALID_UNTIL_MESSAGE);
		}
	}

	/**
	 * Lista separada por comas; vacía u omitida evalúa a todos los propietarios activos. Los repetidos cuentan una
	 * vez.
	 */
	static Set<UUID> parseOwnerIds(String value) {
		if (value == null || value.isBlank()) {
			return Set.of();
		}
		String[] items = value.split(",", -1);
		if (items.length > ValidityCheckQuery.MAX_OWNER_IDS) {
			throw new InvalidQueryParameterException(ValidityCheckQuery.OWNER_IDS_LIMIT_MESSAGE);
		}
		Set<UUID> ids = new LinkedHashSet<>();
		for (String item : items) {
			try {
				ids.add(UUID.fromString(item.strip()));
			}
			catch (IllegalArgumentException ex) {
				throw new InvalidQueryParameterException(
						"El parámetro 'owner_ids' debe ser una lista de UUID separados por coma.");
			}
		}
		return ids;
	}

	private static int parseInt(String value, String message) {
		try {
			return Integer.parseInt(value.strip());
		}
		catch (NumberFormatException ex) {
			throw new InvalidQueryParameterException(message);
		}
	}

	private static ValidityCheckResponse toResponse(ValidityCheckPage page) {
		List<EligibleOwnerResponse> data = page.data()
			.stream()
			.map(owner -> new EligibleOwnerResponse(owner.ownerId(), owner.licensePlate(), owner.fullName(),
					owner.documents()
						.stream()
						.map(document -> new ValidDocumentResponse(document.documentType(), document.expirationDate()))
						.toList()))
			.toList();
		return new ValidityCheckResponse(page.page(), page.size(), page.totalElements(), page.totalPages(), data);
	}

}
