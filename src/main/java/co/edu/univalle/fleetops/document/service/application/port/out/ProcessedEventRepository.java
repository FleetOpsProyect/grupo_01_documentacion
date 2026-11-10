package co.edu.univalle.fleetops.document.service.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Puerto de salida: eventos ya consumidos (tabla processed_events, DAS sec. 8.2). Permite descartar las entregas
 * repetidas de SQS (Idempotent Consumer).
 */
public interface ProcessedEventRepository {

	boolean exists(UUID eventId);

	/**
	 * Guarda el event_id en la misma transacción que el cambio de la réplica (HU-DOC-10, criterio 3).
	 */
	void record(UUID eventId, String eventType, String source, Instant processedAt);

}
