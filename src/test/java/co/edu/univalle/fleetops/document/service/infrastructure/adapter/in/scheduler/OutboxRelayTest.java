package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.scheduler;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase.EventFailure;
import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase.PublicationResult;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.OutboxEventsAppended;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

	private static final UUID EVENT_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000e1");

	private static final PublicationResult NOTHING = new PublicationResult(0, List.of(), null, false);

	private static final PublicationResult FULL_BATCH = new PublicationResult(50, List.of(), null, true);

	private static final PublicationResult PARTIAL_BATCH = new PublicationResult(3,
			List.of(new EventFailure(EVENT_ID, "InvalidParameter")), null, false);

	private static final PublicationResult INTERRUPTED = new PublicationResult(0, List.of(),
			new EventFailure(EVENT_ID, "SNS no está disponible"), false);

	@Mock
	private PublishPendingEventsUseCase useCase;

	private OutboxRelay relay(Duration interval) {
		return new OutboxRelay(this.useCase, interval);
	}

	@Test
	void should_drain_full_batches_until_the_outbox_is_empty() {
		// Arrange (D-4: los acumulados se publican lote a lote)
		given(this.useCase.publishPending()).willReturn(FULL_BATCH, FULL_BATCH, PARTIAL_BATCH);
		OutboxRelay relay = relay(Duration.ofSeconds(2));
		relay.start();

		// Act
		boolean healthy = relay.drain();

		// Assert
		assertThat(healthy).isTrue();
		relay.stop();
	}

	@Test
	void should_report_a_transient_failure_and_its_recovery() {
		given(this.useCase.publishPending()).willReturn(INTERRUPTED, INTERRUPTED, NOTHING);
		OutboxRelay relay = relay(Duration.ofSeconds(2));

		assertThat(relay.drain()).isFalse();
		assertThat(relay.drain()).isFalse();
		assertThat(relay.drain()).isTrue();
		verify(this.useCase, times(3)).publishPending();
	}

	@Test
	void should_survive_an_unavailable_database() {
		given(this.useCase.publishPending()).willThrow(new IllegalStateException("base de datos caída"));
		OutboxRelay relay = relay(Duration.ofSeconds(2));

		assertThat(relay.drain()).isFalse();
		assertThat(relay.drain()).isFalse();
	}

	@Test
	void should_poll_periodically_and_wake_up_after_a_commit() {
		// Arrange: intervalo largo, para que solo un aviso de commit explique la segunda lectura
		given(this.useCase.publishPending()).willReturn(NOTHING);
		OutboxRelay relay = relay(Duration.ofMinutes(5));

		// Act
		relay.start();
		relay.start();
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(this.useCase).publishPending());
		relay.onEventsAppended(new OutboxEventsAppended());

		// Assert
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(this.useCase, times(2)).publishPending());
		assertThat(relay.isRunning()).isTrue();
		relay.stop();
		relay.stop();
		assertThat(relay.isRunning()).isFalse();
	}

	@Test
	void should_keep_polling_while_the_broker_is_unavailable() {
		given(this.useCase.publishPending()).willReturn(INTERRUPTED);
		OutboxRelay relay = relay(Duration.ofMillis(20));

		relay.start();

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(this.useCase, atLeast(3)).publishPending());
		relay.stop();
	}

	@Test
	void should_collapse_repeated_signals() {
		OutboxRelay relay = relay(Duration.ofSeconds(2));

		relay.signal();
		relay.signal();

		assertThat(relay.isRunning()).isFalse();
	}

	@Test
	void should_reject_missing_dependencies() {
		Duration interval = Duration.ofSeconds(2);
		assertThatNullPointerException().isThrownBy(() -> new OutboxRelay(null, interval)).withMessage("useCase");
		assertThatNullPointerException().isThrownBy(() -> new OutboxRelay(this.useCase, null))
			.withMessage("pollInterval");
	}

}
