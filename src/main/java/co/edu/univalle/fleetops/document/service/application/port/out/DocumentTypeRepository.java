package co.edu.univalle.fleetops.document.service.application.port.out;

import java.util.Optional;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeRules;

/**
 * Puerto de salida: catálogo de tipos de documento y sus reglas (ADR-DOC-10).
 */
public interface DocumentTypeRepository {

	Optional<DocumentTypeRules> findByCode(DocumentTypeCode code);

}
