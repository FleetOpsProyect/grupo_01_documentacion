package co.edu.univalle.fleetops.document.service.application.port.in;

/**
 * Puerto de entrada: anular un documento registrado por error (HU-DOC-04).
 */
public interface CancelDocumentUseCase {

	/**
	 * Anula un documento conservando su información e historial.
	 *
	 * @param command datos de la anulación
	 * @return documento anulado
	 * @throws co.edu.univalle.fleetops.document.service.domain.exception.ResourceNotFoundException
	 * si el documento no existe
	 * @throws co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException
	 * si el documento no permite modificaciones
	 * @throws co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException
	 * si el motivo de anulación no es válido
	 */
	DocumentView cancel(CancelDocumentCommand command);

}
