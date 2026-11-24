package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.scheduler;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;

import co.edu.univalle.fleetops.document.service.application.port.in.CheckExpirationsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult.DocumentFailure;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest.CorrelationIdFilter;

/**
 * Adaptador de entrada programado: dispara la verificación de vigencia (HU-DOC-06, ADR-DOC-02).
 *
 * <ul>
 * <li>Todos los días según {@code fleetops.documents.expiration-job.cron} (01:00) en BUSINESS_TIME_ZONE
 * (criterio 1).</li>
 * <li>Al arrancar, en segundo plano, para recuperar la ejecución del día si el servicio estuvo caído
 * (criterio 5). Si la fecha ya se verificó, termina sin evaluar nada.</li>
 * </ul>
 *
 * <p>Nunca propaga errores: una falla queda en el log y la ejecución se reintenta al día siguiente o al arrancar.
 * El resumen y cada documento fallido van al log con el id de la ejecución como correlation_id (criterio 7).
 */
public class ExpirationCheckScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExpirationCheckScheduler.class);

	private final CheckExpirationsUseCase useCase;

	private final TaskScheduler taskScheduler;

	public ExpirationCheckScheduler(CheckExpirationsUseCase useCase, TaskScheduler taskScheduler) {
		this.useCase = Objects.requireNonNull(useCase, "useCase");
		this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
	}

	@Scheduled(cron = "${fleetops.documents.expiration-job.cron}", zone = "${fleetops.documents.business-time-zone}")
	public void runDaily() {
		run("programada");
	}

	/**
	 * Se ejecuta en el hilo del planificador, para no demorar el arranque ni la disponibilidad del servicio.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void recoverOnStartup() {
		this.taskScheduler.schedule(() -> run("recuperación al arrancar"), Instant.now());
	}

	void run(String trigger) {
		try {
			report(trigger, this.useCase.checkExpirations());
		}
		catch (RuntimeException ex) {
			LOGGER.error("La verificación de vigencia ({}) falló; se reintentará en la próxima ejecución", trigger,
					ex);
		}
	}

	private static void report(String trigger, ExpirationCheckResult result) {
		if (!result.executed()) {
			LOGGER.info("Verificación de vigencia ({}): la fecha {} ya fue verificada", trigger,
					result.businessDate());
			return;
		}
		MDC.put(CorrelationIdFilter.MDC_KEY, result.runId().toString());
		try {
			for (DocumentFailure failure : result.failures()) {
				LOGGER.error("Verificación de vigencia: falló el documento {}: {}", failure.documentId(),
						failure.reason());
			}
			LOGGER.info("Verificación de vigencia ({}) del {}: evaluados {}, emitidos {}, fallidos {}", trigger,
					result.businessDate(), result.evaluated(), result.issued(), result.failed());
		}
		finally {
			MDC.remove(CorrelationIdFilter.MDC_KEY);
		}
	}

}
