package co.edu.univalle.fleetops.document.service.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase.EventFailure;
import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase.PublicationResult;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.EventPublicationException;
import co.edu.univalle.fleetops.document.service.application.port.out.EventPublisher;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository.PendingEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PublishPendingEventsServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-24T15:00:00Z");

	private static final PendingEvent FIRST = event("019aaa00-0000-7000-8000-0000000000e1");

	private static final PendingEvent SECOND = event("019aaa00-0000-7000-8000-0000000000e2");

	private static final PendingEvent THIRD = event("019aaa00-0000-7000-8000-0000000000e3");

	@Mock
	private OutboxRepository outbox;

	@Mock
	private EventPublisher publisher;

	@Mock
	private BusinessClock clock;

	private static PendingEvent event(String id) {
		return new PendingEvent(UUID.fromString(id), "document_registered_event", "fleetops_document_events",
				"{\"event_id\":\"" + id + "\"}", null);
	}

	private PublishPendingEventsService service(int batchSize) {
		return new PublishPendingEventsService(this.outbox, this.publisher, this.clock, batchSize);
	}

	@Test
	void should_publish_each_event_in_order_and_mark_it_published() {
		// Arrange (D-4: del más antiguo al más reciente, como los devuelve el outbox)
		given(this.outbox.lockPending(50)).willReturn(List.of(FIRST, SECOND));
		given(this.clock.now()).willReturn(NOW);

		// Act
		PublicationResult result = service(50).publishPending();

		// Assert
		assertThat(result).isEqualTo(new PublicationResult(2, List.of(), null, false));
		assertThat(result.interrupted()).isFalse();
		InOrder order = inOrder(this.publisher, this.outbox);
		order.verify(this.publisher).publish(FIRST);
		order.verify(this.outbox).markPublished(FIRST.eventId(), NOW);
		order.verify(this.publisher).publish(SECOND);
		order.verify(this.outbox).markPublished(SECOND.eventId(), NOW);
	}

	@Test
	void should_report_that_a_full_batch_may_have_more_events() {
		given(this.outbox.lockPending(2)).willReturn(List.of(FIRST, SECOND));
		given(this.clock.now()).willReturn(NOW);

		assertThat(service(2).publishPending().mayHaveMore()).isTrue();
	}

	@Test
	void should_do_nothing_without_pending_events() {
		given(this.outbox.lockPending(50)).willReturn(List.of());

		PublicationResult result = service(50).publishPending();

		assertThat(result).isEqualTo(new PublicationResult(0, List.of(), null, false));
		verifyNoInteractions(this.publisher, this.clock);
	}

	@Test
	void should_mark_failed_an_event_the_broker_rejects_and_continue() {
		// Arrange (D-2: error propio del evento)
		given(this.outbox.lockPending(50)).willReturn(List.of(FIRST, SECOND));
		given(this.clock.now()).willReturn(NOW);
		willThrow(new EventPublicationException("SNS rechazó el evento (InvalidParameter)", false, null))
			.given(this.publisher)
			.publish(FIRST);

		// Act
		PublicationResult result = service(50).publishPending();

		// Assert
		assertThat(result.published()).isEqualTo(1);
		assertThat(result.permanentFailures())
			.containsExactly(new EventFailure(FIRST.eventId(), "SNS rechazó el evento (InvalidParameter)"));
		verify(this.outbox).recordFailure(FIRST.eventId(), "SNS rechazó el evento (InvalidParameter)", true);
		verify(this.outbox).markPublished(SECOND.eventId(), NOW);
	}

	@Test
	void should_stop_the_batch_on_a_transient_error_and_keep_the_rest_pending() {
		// Arrange (D-2: SNS no disponible; sin espera creciente)
		given(this.outbox.lockPending(3)).willReturn(List.of(FIRST, SECOND, THIRD));
		given(this.clock.now()).willReturn(NOW);
		willAnswer(invocation -> {
			if (SECOND.equals(invocation.getArgument(0))) {
				throw new EventPublicationException("SNS no está disponible", true, null);
			}
			return null;
		}).given(this.publisher).publish(any(PendingEvent.class));

		// Act
		PublicationResult result = service(3).publishPending();

		// Assert: no insiste contra un broker caído ni salta el orden
		assertThat(result.published()).isEqualTo(1);
		assertThat(result.interrupted()).isTrue();
		assertThat(result.mayHaveMore()).isFalse();
		assertThat(result.transientFailure()).isEqualTo(new EventFailure(SECOND.eventId(), "SNS no está disponible"));
		verify(this.outbox).markPublished(FIRST.eventId(), NOW);
		verify(this.outbox).recordFailure(SECOND.eventId(), "SNS no está disponible", false);
		verify(this.publisher, never()).publish(THIRD);
		verify(this.outbox, never()).markPublished(SECOND.eventId(), NOW);
		verify(this.outbox, never()).recordFailure(eq(THIRD.eventId()), any(), anyBoolean());
	}

	@Test
	void should_describe_an_error_without_message() {
		given(this.outbox.lockPending(50)).willReturn(List.of(FIRST));
		willThrow(new EventPublicationException(null, true, null)).given(this.publisher).publish(FIRST);

		PublicationResult result = service(50).publishPending();

		assertThat(result.transientFailure().reason()).isEqualTo("EventPublicationException");
	}

	@Test
	void should_reject_missing_dependencies_and_an_empty_batch() {
		assertThatNullPointerException()
			.isThrownBy(() -> new PublishPendingEventsService(null, this.publisher, this.clock, 50))
			.withMessage("outbox");
		assertThatNullPointerException()
			.isThrownBy(() -> new PublishPendingEventsService(this.outbox, null, this.clock, 50))
			.withMessage("publisher");
		assertThatNullPointerException()
			.isThrownBy(() -> new PublishPendingEventsService(this.outbox, this.publisher, null, 50))
			.withMessage("clock");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new PublishPendingEventsService(this.outbox, this.publisher, this.clock, 0));
	}

	@Test
	void should_validate_the_types_of_the_ports() {
		assertThat(new EventPublicationException("x", true, null).retryable()).isTrue();
		assertThatIllegalArgumentException().isThrownBy(() -> new PublicationResult(-1, List.of(), null, false));
		assertThatNullPointerException().isThrownBy(() -> new PublicationResult(0, null, null, false))
			.withMessage("permanentFailures");
		assertThatNullPointerException().isThrownBy(() -> new EventFailure(null, "x")).withMessage("eventId");
		assertThatNullPointerException().isThrownBy(() -> new EventFailure(FIRST.eventId(), null))
			.withMessage("reason");
		UUID id = FIRST.eventId();
		assertThatNullPointerException().isThrownBy(() -> new PendingEvent(null, "t", "topic", "{}", null))
			.withMessage("eventId");
		assertThatNullPointerException().isThrownBy(() -> new PendingEvent(id, null, "topic", "{}", null))
			.withMessage("eventType");
		assertThatNullPointerException().isThrownBy(() -> new PendingEvent(id, "t", null, "{}", null))
			.withMessage("topic");
		assertThatNullPointerException().isThrownBy(() -> new PendingEvent(id, "t", "topic", null, null))
			.withMessage("payload");
	}

}
