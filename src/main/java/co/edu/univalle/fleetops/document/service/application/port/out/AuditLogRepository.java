package co.edu.univalle.fleetops.document.service.application.port.out;

import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.Document;

/**
 * Puerto de salida: auditoría interna de cada cambio sobre un documento (DAS sec. 4.2 «Responsabilidad»,
 * tabla {@code document_audit_log}).
 */
public interface AuditLogRepository {

	/**
	 * Registra la acción REGISTERED con los datos del documento nuevo.
	 * @param correlationId correlación de la petición; vacío fuera de una petición HTTP
	 */
	void recordRegistration(Document document, UUID correlationId);

}
