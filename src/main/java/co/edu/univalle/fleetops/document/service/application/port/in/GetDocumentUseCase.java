package co.edu.univalle.fleetops.document.service.application.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: consultar un documento por su indentificador.
 * (HU-DOC-03).
 */
public interface GetDocumentUseCase {
	/**
	 * Consulta un documento y devuelve su vigencia actual.
	 *
	 * @param documentId identificador del documento
	 * @return documento con estado y días restantes recalculados
	 * @throws co.edu.univalle.fleetops.document.service.domain.exception.ResourceNotFoundException
	 * si el documento no existe
	 */
	DocumentView getById(UUID documentId);
}
