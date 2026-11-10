package co.edu.univalle.fleetops.document.service.application.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaAction;
import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaEvent;
import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaSyncOutcome;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.OwnerReplicaRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.ProcessedEventRepository;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SyncOwnerReplicaServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-08T20:00:00Z");

	private static final UUID EVENT_ID = UUID.fromString("019a5f00-0000-7000-8000-00000000d001");

	private static final OwnerRef DRIVER = new OwnerRef(OwnerType.DRIVER,
			UUID.fromString("01960000-0000-7000-8000-000000000201"));

	// 09:00 en Bogotá (-05:00)
	private static final Instant OCCURRED_AT = Instant.parse("2026-11-08T14:00:00Z");

	@Mock
	private OwnerReplicaRepository owners;

	@Mock
	private ProcessedEventRepository processedEvents;

	@Mock
	private BusinessClock clock;

	private SyncOwnerReplicaService service() {
		return new SyncOwnerReplicaService(this.owners, this.processedEvents, this.clock);
	}

	private static OwnerReplicaEvent activation(Instant occurredAt) {
		return new OwnerReplicaEvent(EVENT_ID, "driver_activated_event", "driver", occurredAt, DRIVER,
				OwnerReplicaAction.ACTIVATE, "Ana María Restrepo");
	}

	private static OwnerReplicaEvent deactivation(Instant occurredAt) {
		return new OwnerReplicaEvent(EVENT_ID, "driver_deactivated_event", "driver", occurredAt, DRIVER,
				OwnerReplicaAction.DEACTIVATE, null);
	}

	@Test
	void should_create_the_replica_of_a_new_owner_and_record_the_event() {
		// Arrange
		given(this.clock.now()).willReturn(NOW);
		given(this.owners.lastEventAt(DRIVER)).willReturn(Optional.empty());

		// Act
		OwnerReplicaSyncOutcome outcome = service().sync(activation(OCCURRED_AT));

		// Assert
		assertThat(outcome).isEqualTo(OwnerReplicaSyncOutcome.APPLIED);
		InOrder order = inOrder(this.owners, this.processedEvents);
		order.verify(this.owners).activate(DRIVER, "Ana María Restrepo", OCCURRED_AT, NOW);
		order.verify(this.processedEvents).record(EVENT_ID, "driver_activated_event", "driver", NOW);
	}

	@Test
	void should_reactivate_an_owner_with_a_newer_event() {
		// Arrange
		given(this.clock.now()).willReturn(NOW);
		given(this.owners.lastEventAt(DRIVER)).willReturn(Optional.of(OCCURRED_AT.minusSeconds(60)));

		// Act
		OwnerReplicaSyncOutcome outcome = service().sync(activation(OCCURRED_AT));

		// Assert
		assertThat(outcome).isEqualTo(OwnerReplicaSyncOutcome.APPLIED);
		verify(this.owners).activate(DRIVER, "Ana María Restrepo", OCCURRED_AT, NOW);
	}

	@Test
	void should_deactivate_a_known_owner() {
		// Arrange
		given(this.clock.now()).willReturn(NOW);
		given(this.owners.lastEventAt(DRIVER)).willReturn(Optional.of(OCCURRED_AT.minusSeconds(60)));

		// Act
		OwnerReplicaSyncOutcome outcome = service().sync(deactivation(OCCURRED_AT));

		// Assert
		assertThat(outcome).isEqualTo(OwnerReplicaSyncOutcome.APPLIED);
		verify(this.owners).deactivate(DRIVER, OCCURRED_AT, NOW);
		verify(this.processedEvents).record(EVENT_ID, "driver_deactivated_event", "driver", NOW);
	}

	@Test
	void should_ignore_a_repeated_event_without_changes() {
		// Arrange (criterio 3)
		given(this.processedEvents.exists(EVENT_ID)).willReturn(true);

		// Act
		OwnerReplicaSyncOutcome outcome = service().sync(activation(OCCURRED_AT));

		// Assert
		assertThat(outcome).isEqualTo(OwnerReplicaSyncOutcome.DUPLICATE);
		verifyNoInteractions(this.owners, this.clock);
		verify(this.processedEvents, never()).record(any(), any(), any(), any());
	}

	@Test
	void should_ignore_an_event_older_than_the_last_applied_but_record_it() {
		// Arrange (criterio 4)
		given(this.clock.now()).willReturn(NOW);
		given(this.owners.lastEventAt(DRIVER)).willReturn(Optional.of(OCCURRED_AT.plusSeconds(1)));

		// Act
		OwnerReplicaSyncOutcome outcome = service().sync(deactivation(OCCURRED_AT));

		// Assert
		assertThat(outcome).isEqualTo(OwnerReplicaSyncOutcome.STALE);
		verify(this.owners, never()).deactivate(any(), any(), any());
		verify(this.processedEvents).record(EVENT_ID, "driver_deactivated_event", "driver", NOW);
	}

	@Test
	void should_ignore_an_event_at_the_same_instant_as_the_last_applied() {
		// Arrange (criterio 4: «anterior o igual»)
		given(this.clock.now()).willReturn(NOW);
		given(this.owners.lastEventAt(DRIVER)).willReturn(Optional.of(OCCURRED_AT));

		// Act
		OwnerReplicaSyncOutcome outcome = service().sync(activation(OCCURRED_AT));

		// Assert
		assertThat(outcome).isEqualTo(OwnerReplicaSyncOutcome.STALE);
		verify(this.owners, never()).activate(any(), any(), any(), any());
	}

	@Test
	void should_record_without_changes_the_deactivation_of_an_unknown_owner() {
		// Arrange
		given(this.clock.now()).willReturn(NOW);
		given(this.owners.lastEventAt(DRIVER)).willReturn(Optional.empty());

		// Act
		OwnerReplicaSyncOutcome outcome = service().sync(deactivation(OCCURRED_AT));

		// Assert
		assertThat(outcome).isEqualTo(OwnerReplicaSyncOutcome.UNKNOWN_OWNER);
		verify(this.owners, never()).deactivate(any(), any(), any());
		verify(this.processedEvents).record(EVENT_ID, "driver_deactivated_event", "driver", NOW);
	}

	@Test
	void should_reject_missing_dependencies_and_event() {
		assertThatNullPointerException()
			.isThrownBy(() -> new SyncOwnerReplicaService(null, this.processedEvents, this.clock))
			.withMessage("owners");
		assertThatNullPointerException().isThrownBy(() -> new SyncOwnerReplicaService(this.owners, null, this.clock))
			.withMessage("processedEvents");
		assertThatNullPointerException()
			.isThrownBy(() -> new SyncOwnerReplicaService(this.owners, this.processedEvents, null))
			.withMessage("clock");
		assertThatNullPointerException().isThrownBy(() -> service().sync(null)).withMessage("event");
	}

}
