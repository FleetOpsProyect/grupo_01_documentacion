package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.ComplianceUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ComplianceView;
import co.edu.univalle.fleetops.document.service.application.port.in.ComplianceParameterException;
import co.edu.univalle.fleetops.document.service.application.port.in.GetDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentUseCase;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Adaptador de entrada REST de documentos (DAS sec. 7).
 */
@RestController
@RequestMapping("/documents")
public class DocumentController {

	private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

	private final RegisterDocumentUseCase registerDocument;
	private final GetDocumentUseCase getDocument;
	private final ComplianceUseCase compliance;

	public DocumentController(
			RegisterDocumentUseCase registerDocument,
			GetDocumentUseCase getDocument,
			ComplianceUseCase compliance) {

		this.registerDocument = registerDocument;
		this.getDocument = getDocument;
		this.compliance = compliance;
	}

	@GetMapping("/compliance")
	public ComplianceResponse compliance(
			@RequestParam(name = "owner_type", required = false) String ownerType,
			@RequestParam(name = "owner_id", required = false) String ownerId) {
		OwnerType parsedOwnerType;
		UUID parsedOwnerId;
		try {
			if (ownerType == null || ownerId == null) {
				throw new ComplianceParameterException();
			}
			parsedOwnerType = OwnerType.valueOf(ownerType);
			parsedOwnerId = UUID.fromString(ownerId);
		}
		catch (IllegalArgumentException ex) {
			throw new ComplianceParameterException();
		}
		ComplianceView view = this.compliance.evaluate(new OwnerRef(parsedOwnerType, parsedOwnerId));
		List<ComplianceResponse.ComplianceDocumentResponse> documents = view.documents().stream()
				.map(document -> new ComplianceResponse.ComplianceDocumentResponse(document.documentId(),
						document.documentType(), document.status(), document.expirationDate(),
						document.daysRemaining()))
				.toList();
		return new ComplianceResponse(view.ownerType(), view.ownerId(), view.evaluatedOn(), view.compliant(),
				documents, view.missingDocuments());
	}

	/**
	 * Registra un SOAT, una revisión tecnomecánica o una tarjeta de propiedad de un vehículo, o la licencia de un
	 * conductor (HU-DOC-01 y HU-DOC-02). Responde 201 con Location.
	 */
	@PostMapping
	public ResponseEntity<DocumentResponse> register(@Valid @RequestBody RegisterDocumentRequest request) {
		DocumentView view = this.registerDocument.register(DocumentRestMapper.toCommand(request));
		log.info("Documento registrado document_id={} document_type={} owner_type={} document_number={} status={}",
				view.documentId(), view.documentType(), view.ownerType(),
				new DocumentNumber(view.documentNumber()).masked(), view.status());
		return ResponseEntity.created(URI.create("/documents/" + view.documentId()))
				.body(DocumentRestMapper.toResponse(view));
	}

	/**
	 * HU-DOC-03. Consulta un documento por su identificador.
	 *
	 * @param documentId identificador del documento
	 * @return información del documento con su vigencia actual
	 */
	@GetMapping("/{id}")
	public ResponseEntity<DocumentResponse> getById(
			@PathVariable("id") UUID documentId) {

		DocumentView view = this.getDocument.getById(documentId);

		return ResponseEntity.ok(
				DocumentRestMapper.toResponse(view));
	}

}
