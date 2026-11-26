package co.edu.univalle.fleetops.document.service.application.port.in;

/**
 * Puerto de entrada: consultar un listado de documentos (HU-DOC-03).
 */
public interface ListDocumentsUseCase {

	/**
	 * Consulta documentos aplicando filtros y paginación.
	 *
	 * @param query parámetros de la consulta
	 * @return página de documentos con su vigencia actual
	 */
	PageResult<DocumentView> listDocuments(ListDocumentQuery query);

}
