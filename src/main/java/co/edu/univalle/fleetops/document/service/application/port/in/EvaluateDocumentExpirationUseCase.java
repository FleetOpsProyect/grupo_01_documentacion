package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: evaluar un documento en la verificación de vigencia (HU-DOC-06). Cada llamada es una
 * transacción: el estado, la auditoría, el evento en el outbox y los umbrales en issued_alerts se confirman juntos
 * (criterio 6), y el error de un documento no afecta a los demás (criterio 7).
 */
public interface EvaluateDocumentExpirationUseCase {

	/**
	 * @param documentId documento candidato
	 * @param today fecha de negocio de la ejecución
	 * @param runId ejecución del job; se usa como correlation_id del evento y de la auditoría
	 */
	DocumentExpirationOutcome evaluate(UUID documentId, LocalDate today, UUID runId);

	/**
	 * Qué cambió en el documento.
	 *
	 * @param skipped el documento ya no era candidato al evaluarlo (se renovó, se anuló o se borró)
	 * @param alertPublished se publicó document_expiring_soon_event
	 * @param statusChanged se guardó un estado nuevo
	 */
	record DocumentExpirationOutcome(boolean skipped, boolean alertPublished, boolean statusChanged) {

		private static final DocumentExpirationOutcome SKIPPED = new DocumentExpirationOutcome(true, false, false);

		public static DocumentExpirationOutcome skippedOutcome() {
			return SKIPPED;
		}

		public static DocumentExpirationOutcome evaluated(boolean alertPublished, boolean statusChanged) {
			return new DocumentExpirationOutcome(false, alertPublished, statusChanged);
		}

	}

}
