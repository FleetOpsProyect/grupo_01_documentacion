package co.edu.univalle.fleetops.document.service.application.port.out;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de salida: umbrales ya emitidos por documento (tabla {@code issued_alerts}, ADR-DOC-02 y ADR-DOC-03). La
 * restricción única (document_id, threshold_days) es la garantía final contra avisos duplicados.
 */
public interface IssuedAlertRepository {

	/**
	 * Umbrales ya registrados para el documento, con evento o sin él.
	 */
	Set<Integer> issuedThresholds(UUID documentId);

	/**
	 * Registra un umbral en la transacción en curso.
	 *
	 * @param outboxEventId event_id del evento publicado; {@code null} si el umbral se marca sin evento (ADR-DOC-03)
	 */
	void record(UUID documentId, int thresholdDays, UUID outboxEventId, Instant issuedAt);

}
