package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumen de una verificación de vigencia (HU-DOC-06, criterio 7).
 *
 * @param runId id de la ejecución en scheduled_job_runs; vacío si no se ejecutó
 * @param businessDate fecha de negocio verificada
 * @param executed {@code false} si esa fecha ya la verificó esta u otra instancia
 * @param evaluated documentos evaluados, con error o sin él
 * @param issued eventos document_expiring_soon_event publicados
 * @param failures documentos que fallaron, con el motivo
 */
public record ExpirationCheckResult(UUID runId, LocalDate businessDate, boolean executed, int evaluated, int issued,
		List<DocumentFailure> failures) {

	public ExpirationCheckResult {
		Objects.requireNonNull(businessDate, "businessDate");
		failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
		if (executed) {
			Objects.requireNonNull(runId, "runId");
		}
	}

	/**
	 * La fecha ya se verificó: la segunda pasada no evalúa ni emite nada (HU-DOC-06, criterio 4).
	 */
	public static ExpirationCheckResult alreadyRan(LocalDate businessDate) {
		return new ExpirationCheckResult(null, businessDate, false, 0, 0, List.of());
	}

	public int failed() {
		return this.failures.size();
	}

	/**
	 * Error al evaluar un documento; no detiene la verificación de los demás (HU-DOC-06, criterio 7).
	 *
	 * @param documentId documento que falló
	 * @param reason motivo, para el log
	 */
	public record DocumentFailure(UUID documentId, String reason) {

		public DocumentFailure {
			Objects.requireNonNull(documentId, "documentId");
			Objects.requireNonNull(reason, "reason");
		}

	}

}
