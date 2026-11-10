package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaEvent;
import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaSyncOutcome;
import co.edu.univalle.fleetops.document.service.application.port.in.SyncOwnerReplicaUseCase;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest.CorrelationIdFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SqsOwnerEventConsumerTest {

	private static final String QUEUE = "fleetops_document_driver_events_queue";

	private static final String QUEUE_URL = "http://localhost:4566/123456789012/" + QUEUE;

	private static final String CORRELATION_ID = "019a5f00-0000-7000-8000-0000000c0001";

	@Mock
	private SqsClient sqs;

	@Mock
	private SyncOwnerReplicaUseCase useCase;

	private SqsOwnerEventConsumer consumer(Duration retryDelay) {
		return new SqsOwnerEventConsumer(this.sqs, QUEUE, new OwnerEventMessageMapper(JsonMapper.builder().build()),
				this.useCase, 20, 10, retryDelay);
	}

	private SqsOwnerEventConsumer consumer() {
		return consumer(Duration.ofSeconds(5));
	}

	private void givenQueue(Message... messages) {
		given(this.sqs.getQueueUrl(any(GetQueueUrlRequest.class)))
			.willReturn(GetQueueUrlResponse.builder().queueUrl(QUEUE_URL).build());
		given(this.sqs.receiveMessage(any(ReceiveMessageRequest.class)))
			.willReturn(ReceiveMessageResponse.builder().messages(messages).build());
	}

	private static Message message(String id, String body) {
		return Message.builder()
			.messageId(id)
			.receiptHandle("receipt-" + id)
			.body(body)
			.attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "1"))
			.build();
	}

	private static String driverActivated(String eventId) {
		return """
				{"event_id": "%s", "event_type": "driver_activated_event", "occurred_at": "2026-11-08T09:00:00-05:00",
				 "source": "driver", "correlation_id": "%s",
				 "data": {"driver_id": "01960000-0000-7000-8000-000000000201", "full_name": "Ana María Restrepo"}}
				""".formatted(eventId, CORRELATION_ID);
	}

	@Test
	void should_long_poll_and_delete_each_message_after_processing_it() {
		// Arrange
		givenQueue(message("m1", driverActivated(UUID.randomUUID().toString())),
				message("m2", driverActivated(UUID.randomUUID().toString())));
		given(this.useCase.sync(any())).willReturn(OwnerReplicaSyncOutcome.APPLIED, OwnerReplicaSyncOutcome.DUPLICATE);

		// Act
		int received = consumer().pollOnce();

		// Assert
		assertThat(received).isEqualTo(2);
		ArgumentCaptor<ReceiveMessageRequest> receive = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
		verify(this.sqs).receiveMessage(receive.capture());
		assertThat(receive.getValue().queueUrl()).isEqualTo(QUEUE_URL);
		assertThat(receive.getValue().waitTimeSeconds()).isEqualTo(20);
		assertThat(receive.getValue().maxNumberOfMessages()).isEqualTo(10);
		assertThat(receive.getValue().messageSystemAttributeNames())
			.containsExactly(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
		ArgumentCaptor<DeleteMessageRequest> delete = ArgumentCaptor.forClass(DeleteMessageRequest.class);
		verify(this.sqs, times(2)).deleteMessage(delete.capture());
		assertThat(delete.getAllValues()).extracting(DeleteMessageRequest::receiptHandle)
			.containsExactly("receipt-m1", "receipt-m2");
	}

	@Test
	void should_resolve_the_queue_url_only_once() {
		// Arrange
		givenQueue();
		SqsOwnerEventConsumer consumer = consumer();

		// Act
		consumer.pollOnce();
		consumer.pollOnce();

		// Assert
		verify(this.sqs, times(1)).getQueueUrl(any(GetQueueUrlRequest.class));
		verify(this.sqs, times(2)).receiveMessage(any(ReceiveMessageRequest.class));
	}

	@Test
	void should_put_the_correlation_id_in_the_mdc_while_processing() {
		// Arrange
		givenQueue(message("m1", driverActivated(UUID.randomUUID().toString())));
		AtomicReference<String> seen = new AtomicReference<>();
		willAnswer(invocation -> {
			seen.set(MDC.get(CorrelationIdFilter.MDC_KEY));
			return OwnerReplicaSyncOutcome.APPLIED;
		}).given(this.useCase).sync(any(OwnerReplicaEvent.class));

		// Act
		consumer().pollOnce();

		// Assert
		assertThat(seen.get()).isEqualTo(CORRELATION_ID);
		assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void should_not_delete_an_invalid_message_so_it_reaches_the_dlq() {
		// Arrange (criterio 6)
		givenQueue(message("m1", "{roto"));

		// Act
		consumer().pollOnce();

		// Assert
		verifyNoInteractions(this.useCase);
		verify(this.sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
	}

	@Test
	void should_not_delete_a_message_whose_processing_fails_and_continue_with_the_next() {
		// Arrange (criterio 6)
		givenQueue(message("m1", driverActivated(UUID.randomUUID().toString())),
				message("m2", driverActivated(UUID.randomUUID().toString())));
		given(this.useCase.sync(any())).willThrow(new IllegalStateException("base de datos caída"))
			.willReturn(OwnerReplicaSyncOutcome.APPLIED);

		// Act
		consumer().pollOnce();

		// Assert
		ArgumentCaptor<DeleteMessageRequest> delete = ArgumentCaptor.forClass(DeleteMessageRequest.class);
		verify(this.sqs).deleteMessage(delete.capture());
		assertThat(delete.getValue().receiptHandle()).isEqualTo("receipt-m2");
	}

	@Test
	void should_propagate_sqs_errors_from_a_poll() {
		// Arrange
		given(this.sqs.getQueueUrl(any(GetQueueUrlRequest.class)))
			.willThrow(SdkClientException.create("Unable to execute HTTP request"));

		// Act + Assert
		assertThatExceptionOfType(SdkClientException.class).isThrownBy(() -> consumer().pollOnce());
	}

	@Test
	void should_keep_retrying_while_sqs_is_unavailable_and_stop_cleanly() {
		// Arrange (criterio 7)
		given(this.sqs.getQueueUrl(any(GetQueueUrlRequest.class)))
			.willThrow(SdkClientException.create("Unable to execute HTTP request"));
		SqsOwnerEventConsumer consumer = consumer(Duration.ofMillis(10));

		// Act
		consumer.start();
		consumer.start();

		// Assert
		assertThat(consumer.isRunning()).isTrue();
		await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> verify(this.sqs, atLeast(3)).getQueueUrl(any(GetQueueUrlRequest.class)));
		consumer.stop();
		consumer.stop();
		assertThat(consumer.isRunning()).isFalse();
	}

	@Test
	void should_poll_continuously_once_started() {
		// Arrange
		givenQueue();
		SqsOwnerEventConsumer consumer = consumer();

		// Act
		consumer.start();

		// Assert
		await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> verify(this.sqs, atLeast(2)).receiveMessage(any(ReceiveMessageRequest.class)));
		consumer.stop();
	}

	@Test
	void should_leave_the_remaining_messages_in_the_queue_when_stopping() {
		// Arrange
		givenQueue(message("m1", driverActivated(UUID.randomUUID().toString())),
				message("m2", driverActivated(UUID.randomUUID().toString())));
		SqsOwnerEventConsumer consumer = consumer();
		willAnswer(invocation -> {
			consumer.stop();
			return OwnerReplicaSyncOutcome.APPLIED;
		}).given(this.useCase).sync(any(OwnerReplicaEvent.class));
		consumer.start();

		// Act + Assert
		await().atMost(Duration.ofSeconds(5)).until(() -> !consumer.isRunning());
		verify(this.useCase, times(1)).sync(any(OwnerReplicaEvent.class));
	}

	@Test
	void should_reject_missing_dependencies() {
		OwnerEventMessageMapper mapper = mock(OwnerEventMessageMapper.class);
		Duration delay = Duration.ofSeconds(1);
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> new SqsOwnerEventConsumer(null, QUEUE, mapper, this.useCase, 20, 10, delay));
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> new SqsOwnerEventConsumer(this.sqs, null, mapper, this.useCase, 20, 10, delay));
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> new SqsOwnerEventConsumer(this.sqs, QUEUE, null, this.useCase, 20, 10, delay));
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> new SqsOwnerEventConsumer(this.sqs, QUEUE, mapper, null, 20, 10, delay));
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> new SqsOwnerEventConsumer(this.sqs, QUEUE, mapper, this.useCase, 20, 10, null));
	}

}
