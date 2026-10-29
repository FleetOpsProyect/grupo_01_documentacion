package co.edu.univalle.fleetops.document.service.application.port.out;

import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;

/**
 * Puerto de salida: persistencia de documentos (Repository).
 */
public interface DocumentRepository {

	/**
	 * La combinación tipo + número es única entre los documentos que reservan su número, es decir, los no anulados
	 * ({@link co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus#reservesDocumentNumber()},
	 * DAS sec. 6.3).
	 */
	boolean existsNotCancelledByTypeAndNumber(DocumentTypeCode documentType, DocumentNumber documentNumber);

	/**
	 * Un propietario tiene como máximo un documento activo (VALID, EXPIRING_SOON o EXPIRED) por tipo (ADR-DOC-11).
	 */
	boolean existsActiveByOwnerAndType(OwnerRef owner, DocumentTypeCode documentType);

	/**
	 * Guarda un documento nuevo.
	 * @throws co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException si la base de
	 * datos rechaza el documento por unicidad, por ejemplo con dos registros simultáneos
	 */
	void save(Document document);

}
