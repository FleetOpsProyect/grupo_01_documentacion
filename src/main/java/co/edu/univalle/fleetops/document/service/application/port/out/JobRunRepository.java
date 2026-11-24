package co.edu.univalle.fleetops.document.service.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: ejecuciones de los jobs programados (tabla {@code scheduled_job_runs}, ADR-DOC-02). La
 * restricción única (job_name, business_date) garantiza una sola ejecución por job y fecha de negocio entre todas
 * las instancias del servicio.
 */
public interface JobRunRepository {

	/**
	 * Reclama la ejecución del job para la fecha de negocio. Cada llamada se confirma en su propia transacción.
	 *
	 * @return el id de la ejecución si esta instancia la obtuvo; vacío si ya terminó o la está haciendo otra
	 * instancia
	 */
	Optional<UUID> tryStart(String jobName, LocalDate businessDate, Instant startedAt);

	/**
	 * Cierra la ejecución con su resumen, en su propia transacción.
	 */
	void finish(UUID runId, RunStatus status, RunSummary summary, Instant finishedAt);

	/**
	 * Estado final de una ejecución.
	 */
	enum RunStatus {

		COMPLETED,

		FAILED

	}

	/**
	 * Resumen de la ejecución (HU-DOC-06, criterio 7).
	 *
	 * @param evaluated documentos evaluados, con error o sin él
	 * @param issued eventos de alerta publicados
	 * @param failed documentos que fallaron
	 */
	record RunSummary(int evaluated, int issued, int failed) {

		public RunSummary {
			if (evaluated < 0 || issued < 0 || failed < 0) {
				throw new IllegalArgumentException("Los contadores de la ejecución no pueden ser negativos");
			}
		}

	}

}
