package co.edu.univalle.fleetops.document.service.application.port.out;

import java.time.Instant;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;

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

	/**
	 * Registra la acción STATUS_CHANGED cuando el job de vigencia guarda un estado nuevo (HU-DOC-06, criterio 6).
	 * @param correlationId correlación de la ejecución del job
	 */
	void recordStatusChange(UUID documentId, DocumentStatus previous, DocumentStatus current, UUID correlationId,
			Instant changedAt);

}
