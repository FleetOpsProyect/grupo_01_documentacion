package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.net.URI;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentUseCase;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;

/**
 * Adaptador de entrada REST de documentos (DAS sec. 7).
 */
@RestController
@RequestMapping("/documents")
public class DocumentController {

	private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

	private final RegisterDocumentUseCase registerDocument;

	public DocumentController(RegisterDocumentUseCase registerDocument) {
		this.registerDocument = registerDocument;
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

}
