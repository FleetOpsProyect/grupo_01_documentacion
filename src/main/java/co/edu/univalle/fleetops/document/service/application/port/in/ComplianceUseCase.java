package co.edu.univalle.fleetops.document.service.application.port.in;

import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;

/**
 * Caso de uso de consulta de cumplimiento documental (HU-DOC-09).
 */
public interface ComplianceUseCase {

	ComplianceView evaluate(OwnerRef owner);

}
