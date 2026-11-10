package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaSyncOutcome;
import co.edu.univalle.fleetops.document.service.application.port.in.SyncOwnerReplicaUseCase;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest.CorrelationIdFilter;

/**
 * Consumidor de una cola SQS propia (HU-DOC-10): un hilo que lee con long polling, traduce cada mensaje con
 * {@link OwnerEventMessageMapper} e invoca {@link SyncOwnerReplicaUseCase}.
 *
 * <ul>
 * <li>Borra el mensaje solo después de procesarlo con éxito, incluidos los repetidos y los antiguos (criterios 1, 3
 * y 4).</li>
 * <li>Un mensaje inválido o un error de procesamiento se registran en el log y el mensaje no se borra: vuelve a la
 * cola al vencer el visibility timeout y, tras 3 recepciones, la redrive policy lo envía a la DLQ (criterio
 * 6).</li>
 * <li>Si SQS no responde, registra el error y reintenta tras {@code retryDelay}; los endpoints REST siguen
 * funcionando porque el hilo es independiente (criterio 7).</li>
 * </ul>
 *
 * <p>Patrones: Polling Consumer, Idempotent Consumer, Retry y Dead Letter Channel.
 */
public final class SqsOwnerEventConsumer implements SmartLifecycle {

	private static final Logger LOGGER = LoggerFactory.getLogger(SqsOwnerEventConsumer.class);

	private static final Duration STOP_TIMEOUT = Duration.ofSeconds(2);

	private final SqsClient sqs;

	private final String queueName;

	private final OwnerEventMessageMapper mapper;

	private final SyncOwnerReplicaUseCase useCase;

	private final int waitTimeSeconds;

	private final int maxMessages;

	private final Duration retryDelay;

	private volatile boolean running;

	private volatile boolean stopRequested;

	private volatile String queueUrl;

	private Thread worker;

	public SqsOwnerEventConsumer(SqsClient sqs, String queueName, OwnerEventMessageMapper mapper,
			SyncOwnerReplicaUseCase useCase, int waitTimeSeconds, int maxMessages, Duration retryDelay) {
		this.sqs = Objects.requireNonNull(sqs, "sqs");
		this.queueName = Objects.requireNonNull(queueName, "queueName");
		this.mapper = Objects.requireNonNull(mapper, "mapper");
		this.useCase = Objects.requireNonNull(useCase, "useCase");
		this.waitTimeSeconds = waitTimeSeconds;
		this.maxMessages = maxMessages;
		this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay");
	}

	@Override
	public synchronized void start() {
		if (this.running) {
			return;
		}
		this.running = true;
		this.stopRequested = false;
		this.worker = Thread.ofPlatform().daemon().name("sqs-consumer-" + this.queueName).start(this::run);
		LOGGER.info("Consumidor SQS iniciado: cola {}", this.queueName);
	}

	@Override
	public synchronized void stop() {
		if (!this.running) {
			return;
		}
		this.running = false;
		this.stopRequested = true;
		this.worker.interrupt();
		try {
			this.worker.join(STOP_TIMEOUT);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		LOGGER.info("Consumidor SQS detenido: cola {}", this.queueName);
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	private void run() {
		while (this.running) {
			try {
				pollOnce();
			}
			catch (SdkException ex) {
				if (!this.running) {
					return;
				}
				LOGGER.warn("SQS no está disponible para la cola {}; se reintenta en {}: {}", this.queueName,
						this.retryDelay, ex.getMessage());
				if (!pause()) {
					return;
				}
			}
		}
	}

	/**
	 * Una lectura con long polling y el procesamiento de los mensajes recibidos.
	 *
	 * @return cuántos mensajes se recibieron
	 * @throws SdkException si SQS no responde; el hilo reintenta tras {@code retryDelay}
	 */
	int pollOnce() {
		String url = resolveQueueUrl();
		List<Message> messages = this.sqs.receiveMessage(ReceiveMessageRequest.builder()
			.queueUrl(url)
			.maxNumberOfMessages(this.maxMessages)
			.waitTimeSeconds(this.waitTimeSeconds)
			.messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
			.build()).messages();
		for (Message message : messages) {
			if (this.stopRequested) {
				// Apagado en curso: el mensaje vuelve a la cola al vencer el visibility timeout
				break;
			}
			handle(url, message);
		}
		return messages.size();
	}

	private String resolveQueueUrl() {
		String url = this.queueUrl;
		if (url == null) {
			url = this.sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(this.queueName).build()).queueUrl();
			this.queueUrl = url;
		}
		return url;
	}

	private void handle(String url, Message message) {
		String receiveCount = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
		try {
			OwnerEventMessage parsed = this.mapper.map(message.body());
			if (parsed.correlationId() != null) {
				MDC.put(CorrelationIdFilter.MDC_KEY, parsed.correlationId());
			}
			OwnerReplicaSyncOutcome outcome = this.useCase.sync(parsed.event());
			delete(url, message);
			LOGGER.info("Evento {} {} de la cola {}: {}", parsed.event().eventType(), parsed.event().eventId(),
					this.queueName, outcome);
		}
		catch (InvalidOwnerEventMessageException ex) {
			LOGGER.warn("Mensaje inválido en la cola {} (message_id {}, event_id {}, recepción {}): {}. "
					+ "No se borra; tras 3 recepciones pasa a la DLQ", this.queueName, message.messageId(),
					ex.eventId(), receiveCount, ex.getMessage());
		}
		catch (RuntimeException ex) {
			LOGGER.error("Error al procesar el mensaje {} de la cola {} (recepción {}). No se borra; se reintenta "
					+ "al vencer el visibility timeout", message.messageId(), this.queueName, receiveCount, ex);
		}
		finally {
			MDC.remove(CorrelationIdFilter.MDC_KEY);
		}
	}

	private void delete(String url, Message message) {
		this.sqs.deleteMessage(
				DeleteMessageRequest.builder().queueUrl(url).receiptHandle(message.receiptHandle()).build());
	}

	private boolean pause() {
		try {
			Thread.sleep(this.retryDelay);
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
