package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonUseCase;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Endpoint de documentos próximos a vencer (HU-DOC-08).
 *
 * <p>Los parámetros se reciben como texto para responder 400 con los mensajes de la historia de usuario, y no con el
 * mensaje genérico de Spring.
 */
@RestController
@RequestMapping("/documents")
public class ExpiringSoonController {

	private final ExpiringSoonUseCase expiringSoon;

	public ExpiringSoonController(ExpiringSoonUseCase expiringSoon) {
		this.expiringSoon = expiringSoon;
	}

	/**
	 * Devuelve los documentos que vencen entre hoy y hoy + days.
	 */
	@GetMapping("/expiring-soon")
	public ExpiringSoonResponse expiringSoon(@RequestParam(name = "days", required = false) String days,
			@RequestParam(name = "document_type", required = false) String documentType,
			@RequestParam(name = "owner_type", required = false) String ownerType,
			@RequestParam(name = "page", required = false) String page,
			@RequestParam(name = "size", required = false) String size) {
		ExpiringSoonQuery query = new ExpiringSoonQuery(
				QueryParameters.toInt(days, ExpiringSoonQuery.DEFAULT_DAYS, ExpiringSoonQuery.INVALID_DAYS),
				QueryParameters.toEnum(documentType, DocumentTypeCode.class, "document_type"),
				QueryParameters.toEnum(ownerType, OwnerType.class, "owner_type"),
				QueryParameters.toPageQuery(page, size));
		return ExpiringSoonResponse.of(this.expiringSoon.findExpiringSoon(query));
	}

}
