package co.edu.univalle.fleetops.document.service.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.application.port.in.CheckExpirationsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase.DocumentExpirationOutcome;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult.DocumentFailure;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository.RunStatus;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository.RunSummary;
import co.edu.univalle.fleetops.document.service.domain.service.AlertThresholdPolicy;

/**
 * Application Service: verificación diaria de vigencia (HU-DOC-06, ADR-DOC-02).
 *
 * <ol>
 * <li>Reclama la fecha de negocio en scheduled_job_runs: si ya la verificó esta u otra instancia, termina sin
 * evaluar nada (criterio 4). La recuperación al arrancar usa el mismo camino (criterio 5).</li>
 * <li>Evalúa cada candidato con {@link EvaluateDocumentExpirationUseCase}, cada uno en su propia transacción: un
 * error se anota con el document_id y la verificación sigue (criterio 7).</li>
 * <li>Cierra la ejecución con el resumen (evaluados, emitidos, fallidos).</li>
 * </ol>
 *
 * <p>No es transaccional: cada paso se confirma por separado, para que un documento con error no deshaga los demás.
 */
public final class CheckExpirationsService implements CheckExpirationsUseCase {

	/**
	 * Nombre del job en scheduled_job_runs.
	 */
	public static final String JOB_NAME = "document_expiration_check";

	private final JobRunRepository jobRuns;

	private final DocumentRepository documents;

	private final EvaluateDocumentExpirationUseCase evaluator;

	private final BusinessClock clock;

	private final AlertThresholdPolicy alertPolicy;

	public CheckExpirationsService(JobRunRepository jobRuns, DocumentRepository documents,
			EvaluateDocumentExpirationUseCase evaluator, BusinessClock clock, AlertThresholdPolicy alertPolicy) {
		this.jobRuns = Objects.requireNonNull(jobRuns, "jobRuns");
		this.documents = Objects.requireNonNull(documents, "documents");
		this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.alertPolicy = Objects.requireNonNull(alertPolicy, "alertPolicy");
	}

	@Override
	public ExpirationCheckResult checkExpirations() {
		LocalDate today = this.clock.today();
		Optional<UUID> claimed = this.jobRuns.tryStart(JOB_NAME, today, this.clock.now());
		if (claimed.isEmpty()) {
			return ExpirationCheckResult.alreadyRan(today);
		}
		UUID runId = claimed.get();
		Tally tally = new Tally();
		try {
			LocalDate alertHorizon = today.plusDays(this.alertPolicy.maxThresholdDays());
			for (UUID documentId : this.documents.findExpirationCandidateIds(today, alertHorizon)) {
				evaluate(documentId, today, runId, tally);
			}
		}
		catch (RuntimeException ex) {
			this.jobRuns.finish(runId, RunStatus.FAILED, tally.summary(), this.clock.now());
			throw ex;
		}
		this.jobRuns.finish(runId, RunStatus.COMPLETED, tally.summary(), this.clock.now());
		return new ExpirationCheckResult(runId, today, true, tally.evaluated, tally.issued, tally.failures);
	}

	private void evaluate(UUID documentId, LocalDate today, UUID runId, Tally tally) {
		try {
			DocumentExpirationOutcome outcome = this.evaluator.evaluate(documentId, today, runId);
			if (!outcome.skipped()) {
				tally.evaluated++;
			}
			if (outcome.alertPublished()) {
				tally.issued++;
			}
		}
		catch (RuntimeException ex) {
			tally.evaluated++;
			tally.failures.add(new DocumentFailure(documentId, describe(ex)));
		}
	}

	private static String describe(RuntimeException ex) {
		String type = ex.getClass().getSimpleName();
		return (ex.getMessage() != null) ? type + ": " + ex.getMessage() : type;
	}

	/**
	 * Contadores de una ejecución.
	 */
	private static final class Tally {

		private int evaluated;

		private int issued;

		private final List<DocumentFailure> failures = new ArrayList<>();

		RunSummary summary() {
			return new RunSummary(this.evaluated, this.issued, this.failures.size());
		}

	}

}
