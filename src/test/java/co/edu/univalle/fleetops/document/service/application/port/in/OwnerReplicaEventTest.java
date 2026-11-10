package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OwnerReplicaEventTest {

	private static final UUID EVENT_ID = UUID.fromString("019a5f00-0000-7000-8000-00000000d001");

	private static final Instant OCCURRED_AT = Instant.parse("2026-11-08T14:00:00Z");

	private static final OwnerRef DRIVER = new OwnerRef(OwnerType.DRIVER,
			UUID.fromString("01960000-0000-7000-8000-000000000201"));

	@Test
	void should_create_an_activation_with_its_label() {
		OwnerReplicaEvent event = new OwnerReplicaEvent(EVENT_ID, "driver_activated_event", "driver", OCCURRED_AT,
				DRIVER, OwnerReplicaAction.ACTIVATE, "Ana María Restrepo");

		assertThat(event.label()).isEqualTo("Ana María Restrepo");
		assertThat(event.action()).isEqualTo(OwnerReplicaAction.ACTIVATE);
	}

	@Test
	void should_create_a_deactivation_without_label() {
		OwnerReplicaEvent event = new OwnerReplicaEvent(EVENT_ID, "driver_deactivated_event", "driver", OCCURRED_AT,
				DRIVER, OwnerReplicaAction.DEACTIVATE, null);

		assertThat(event.label()).isNull();
	}

	@Test
	void should_reject_missing_fields() {
		assertThatNullPointerException().isThrownBy(() -> new OwnerReplicaEvent(null, "t", "s", OCCURRED_AT, DRIVER,
				OwnerReplicaAction.DEACTIVATE, null)).withMessage("eventId");
		assertThatNullPointerException().isThrownBy(() -> new OwnerReplicaEvent(EVENT_ID, null, "s", OCCURRED_AT,
				DRIVER, OwnerReplicaAction.DEACTIVATE, null)).withMessage("eventType");
		assertThatNullPointerException().isThrownBy(() -> new OwnerReplicaEvent(EVENT_ID, "t", null, OCCURRED_AT,
				DRIVER, OwnerReplicaAction.DEACTIVATE, null)).withMessage("source");
		assertThatNullPointerException().isThrownBy(() -> new OwnerReplicaEvent(EVENT_ID, "t", "s", null, DRIVER,
				OwnerReplicaAction.DEACTIVATE, null)).withMessage("occurredAt");
		assertThatNullPointerException().isThrownBy(() -> new OwnerReplicaEvent(EVENT_ID, "t", "s", OCCURRED_AT,
				null, OwnerReplicaAction.DEACTIVATE, null)).withMessage("owner");
		assertThatNullPointerException().isThrownBy(() -> new OwnerReplicaEvent(EVENT_ID, "t", "s", OCCURRED_AT,
				DRIVER, null, null)).withMessage("action");
	}

	@Test
	void should_require_the_label_in_an_activation() {
		assertThatNullPointerException().isThrownBy(() -> new OwnerReplicaEvent(EVENT_ID, "driver_activated_event",
				"driver", OCCURRED_AT, DRIVER, OwnerReplicaAction.ACTIVATE, null)).withMessage("label");
	}

}
