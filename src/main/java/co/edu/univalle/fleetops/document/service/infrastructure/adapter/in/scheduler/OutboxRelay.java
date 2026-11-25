package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.scheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase.EventFailure;
import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase.PublicationResult;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.OutboxEventsAppended;

/**
 * Relevo del outbox (Transactional Outbox, ADR-DOC-05): un hilo propio que publica los eventos pendientes.
 *
 * <ul>
 * <li>Vacía el outbox lote a lote mientras haya pendientes, del más antiguo al más reciente (decisión D-4: también
 * los acumulados mientras la mensajería estuvo apagada).</li>
 * <li>Después espera {@code poll-interval} (2 s) o hasta que un commit guarde eventos nuevos
 * ({@link OutboxEventsAppended}), lo que ocurra primero (decisión D-8).</li>
 * <li>Si el broker falla de forma transitoria, espera el intervalo completo antes de reintentar, sin espera creciente
 * (decisión D-2) y sin que los commits lo despierten antes.</li>
 * <li>Nunca propaga errores: con AWS SNS o la base de datos caídos, la API sigue respondiendo y los eventos esperan
 * en el outbox (DAS sec. 10).</li>
 * </ul>
 */
public final class OutboxRelay implements SmartLifecycle {

	private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

	private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

	private final PublishPendingEventsUseCase useCase;

	private final Duration pollInterval;

	private final Semaphore wakeUp = new Semaphore(0);

	private volatile boolean running;

	private boolean failing;

	private Thread worker;

	public OutboxRelay(PublishPendingEventsUseCase useCase, Duration pollInterval) {
		this.useCase = Objects.requireNonNull(useCase, "useCase");
		this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
	}

	@Override
	public synchronized void start() {
		if (this.running) {
			return;
		}
		this.running = true;
		this.worker = Thread.ofPlatform().daemon().name("outbox-relay").start(this::run);
		LOGGER.info("Relevo del outbox iniciado: consulta cada {}", this.pollInterval);
	}

	@Override
	public synchronized void stop() {
		if (!this.running) {
			return;
		}
		this.running = false;
		this.wakeUp.release();
		try {
			this.worker.join(STOP_TIMEOUT);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		LOGGER.info("Relevo del outbox detenido");
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	/**
	 * Un commit guardó eventos: publicarlos sin esperar al siguiente ciclo.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onEventsAppended(OutboxEventsAppended appended) {
		signal();
	}

	void signal() {
		if (this.wakeUp.availablePermits() == 0) {
			this.wakeUp.release();
		}
	}

	private void run() {
		while (this.running) {
			boolean healthy = drain();
			if (!pause(healthy)) {
				return;
			}
		}
	}

	/**
	 * Publica lotes mientras haya pendientes.
	 *
	 * @return {@code false} si un error transitorio detuvo la publicación
	 */
	boolean drain() {
		try {
			PublicationResult result;
			do {
				result = this.useCase.publishPending();
				report(result);
			}
			while (this.running && result.mayHaveMore());
			return !result.interrupted();
		}
		catch (RuntimeException ex) {
			// Por ejemplo, la base de datos no responde: se reintenta en el próximo ciclo
			if (!this.failing) {
				LOGGER.warn("El relevo del outbox no pudo leer los eventos pendientes; se reintenta cada {}",
						this.pollInterval, ex);
				this.failing = true;
			}
			return false;
		}
	}

	private void report(PublicationResult result) {
		for (EventFailure failure : result.permanentFailures()) {
			LOGGER.error("El broker rechazó el evento {} y quedó en FAILED: {}", failure.eventId(), failure.reason());
		}
		if (result.published() > 0) {
			LOGGER.info("Outbox: {} eventos publicados", result.published());
		}
		if (result.interrupted()) {
			if (!this.failing) {
				EventFailure failure = result.transientFailure();
				LOGGER.warn("No se pudo publicar el evento {}; los pendientes esperan y se reintenta cada {}: {}",
						failure.eventId(), this.pollInterval, failure.reason());
				this.failing = true;
			}
		}
		else if (this.failing) {
			LOGGER.info("El relevo del outbox volvió a publicar");
			this.failing = false;
		}
	}

	/**
	 * @return {@code false} si el hilo debe terminar
	 */
	private boolean pause(boolean healthy) {
		try {
			if (healthy) {
				this.wakeUp.tryAcquire(this.pollInterval.toMillis(), TimeUnit.MILLISECONDS);
			}
			else {
				Thread.sleep(this.pollInterval);
			}
			this.wakeUp.drainPermits();
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
