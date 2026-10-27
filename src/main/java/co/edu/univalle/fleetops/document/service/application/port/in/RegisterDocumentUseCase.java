package co.edu.univalle.fleetops.document.service.application.port.in;

/**
 * Puerto de entrada: registrar un SOAT, una revisión tecnomecánica o una tarjeta de propiedad de un vehículo
 * (HU-DOC-01), o la licencia de conducción de un conductor (HU-DOC-02).
 */
public interface RegisterDocumentUseCase {

	/**
	 * @return el documento registrado con su estado calculado
	 * @throws co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException si los
	 * datos no cumplen las reglas del tipo o el propietario no existe o no está activo (422)
	 * @throws co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException si ya existe el
	 * número para el tipo o el propietario ya tiene un documento activo de ese tipo (409)
	 */
	DocumentView register(RegisterDocumentCommand command);

}
