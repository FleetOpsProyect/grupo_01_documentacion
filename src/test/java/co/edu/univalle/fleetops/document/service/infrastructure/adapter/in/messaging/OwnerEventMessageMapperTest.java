package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaAction;
import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaEvent;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OwnerEventMessageMapperTest {

	private static final String EVENT_ID = "019a5f00-0000-7000-8000-00000000d001";

	private static final String DRIVER_ID = "01960000-0000-7000-8000-000000000201";

	private static final String VEHICLE_ID = "01960000-0000-7000-8000-000000000011";

	private static final String CORRELATION_ID = "019a5f00-0000-7000-8000-0000000c0001";

	private final OwnerEventMessageMapper mapper = new OwnerEventMessageMapper(JsonMapper.builder().build());

	/**
	 * Ejemplo entregado por Conductores (08-11-2026), con un campo desconocido en el sobre y otro en data.
	 */
	@Test
	void should_map_the_driver_activated_event_ignoring_unknown_fields() {
		// Arrange
		String body = """
				{"event_id": "%s", "event_type": "driver_activated_event",
				 "occurred_at": "2026-11-08T09:00:00-05:00", "source": "driver",
				 "correlation_id": "%s", "schema_version": 2,
				 "data": {"driver_id": "%s", "full_name": "  Ana María Restrepo  ", "license_number": "123"}}
				""".formatted(EVENT_ID, CORRELATION_ID, DRIVER_ID);

		// Act
		OwnerEventMessage message = this.mapper.map(body);

		// Assert
		OwnerReplicaEvent event = message.event();
		assertThat(event.eventId()).isEqualTo(UUID.fromString(EVENT_ID));
		assertThat(event.eventType()).isEqualTo("driver_activated_event");
		assertThat(event.source()).isEqualTo("driver");
		// -05:00 se compara como instante (criterio 4)
		assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-08T14:00:00Z"));
		assertThat(event.owner()).isEqualTo(new OwnerRef(OwnerType.DRIVER, UUID.fromString(DRIVER_ID)));
		assertThat(event.action()).isEqualTo(OwnerReplicaAction.ACTIVATE);
		assertThat(event.label()).isEqualTo("Ana María Restrepo");
		assertThat(message.correlationId()).isEqualTo(CORRELATION_ID);
	}

	@Test
	void should_map_the_driver_deactivated_event() {
		OwnerEventMessage message = this.mapper.map(envelope("driver_deactivated_event", "driver",
				"{\"driver_id\": \"" + DRIVER_ID + "\"}"));

		assertThat(message.event().action()).isEqualTo(OwnerReplicaAction.DEACTIVATE);
		assertThat(message.event().owner().ownerType()).isEqualTo(OwnerType.DRIVER);
		assertThat(message.event().label()).isNull();
	}

	@Test
	void should_map_the_vehicle_created_event() {
		OwnerEventMessage message = this.mapper.map(envelope("vehicle_created_event", "vehicle",
				"{\"vehicle_id\": \"" + VEHICLE_ID + "\", \"license_plate\": \"JKL012\"}"));

		assertThat(message.event().owner()).isEqualTo(new OwnerRef(OwnerType.VEHICLE, UUID.fromString(VEHICLE_ID)));
		assertThat(message.event().action()).isEqualTo(OwnerReplicaAction.ACTIVATE);
		assertThat(message.event().label()).isEqualTo("JKL012");
	}

	@Test
	void should_map_the_vehicle_decommissioned_event() {
		OwnerEventMessage message = this.mapper.map(envelope("vehicle_decommissioned_event", "vehicle",
				"{\"vehicle_id\": \"" + VEHICLE_ID + "\"}"));

		assertThat(message.event().owner().ownerType()).isEqualTo(OwnerType.VEHICLE);
		assertThat(message.event().action()).isEqualTo(OwnerReplicaAction.DEACTIVATE);
	}

	@Test
	void should_accept_occurred_at_in_utc() {
		String body = envelope("driver_deactivated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}")
			.replace("2026-11-08T09:00:00-05:00", "2026-11-08T14:00:00Z");

		assertThat(this.mapper.map(body).event().occurredAt()).isEqualTo(Instant.parse("2026-11-08T14:00:00Z"));
	}

	@Test
	void should_drop_a_missing_or_malformed_correlation_id() {
		String withoutCorrelation = envelope("driver_deactivated_event", "driver",
				"{\"driver_id\": \"" + DRIVER_ID + "\"}")
			.replace("\"correlation_id\": \"" + CORRELATION_ID + "\",", "");
		String malformed = envelope("driver_deactivated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}")
			.replace(CORRELATION_ID, "no-es-un-uuid");

		assertThat(this.mapper.map(withoutCorrelation).correlationId()).isNull();
		assertThat(this.mapper.map(malformed).correlationId()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "{roto", "[1, 2]", "\"texto\"" })
	void should_reject_a_body_that_is_not_a_json_object(String body) {
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(body))
			.satisfies(ex -> assertThat(ex.eventId()).isNull());
	}

	@Test
	void should_reject_a_null_body() {
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(null))
			.withMessage("el mensaje está vacío");
	}

	@Test
	void should_reject_a_message_without_event_id() {
		String body = """
				{"event_type": "driver_activated_event", "occurred_at": "2026-11-08T09:00:00-05:00",
				 "source": "driver", "data": {"driver_id": "%s", "full_name": "Ana"}}""".formatted(DRIVER_ID);

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(body))
			.withMessage("event_id es obligatorio")
			.satisfies(ex -> assertThat(ex.eventId()).isNull());
	}

	@Test
	void should_reject_an_event_id_that_is_not_a_uuid() {
		String body = envelope("driver_deactivated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}")
			.replace(EVENT_ID, "evt-1");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(body))
			.withMessage("event_id no es un UUID válido")
			.satisfies(ex -> assertThat(ex.eventId()).isEqualTo("evt-1"));
	}

	@Test
	void should_reject_an_unsupported_or_missing_event_type() {
		String unsupported = envelope("driver_updated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}");
		String missing = unsupported.replace("\"event_type\": \"driver_updated_event\",", "");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(unsupported))
			.withMessage("event_type no soportado: driver_updated_event")
			.satisfies(ex -> assertThat(ex.eventId()).isEqualTo(EVENT_ID));
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(missing))
			.withMessage("event_type no soportado: null");
	}

	@Test
	void should_reject_a_message_without_the_entity_id() {
		String body = envelope("driver_activated_event", "driver", "{\"full_name\": \"Ana\"}");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(body))
			.withMessage("data.driver_id es obligatorio");
	}

	@Test
	void should_reject_an_entity_id_that_is_not_a_uuid() {
		String body = envelope("vehicle_decommissioned_event", "vehicle", "{\"vehicle_id\": 42}");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(body))
			.withMessage("data.vehicle_id es obligatorio");
	}

	@Test
	void should_reject_data_that_is_not_an_object() {
		String body = envelope("driver_deactivated_event", "driver", "\"" + DRIVER_ID + "\"");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(body))
			.withMessage("data debe ser un objeto JSON");
	}

	@Test
	void should_reject_a_missing_or_invalid_occurred_at() {
		String base = envelope("driver_deactivated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}");
		String missing = base.replace("\"occurred_at\": \"2026-11-08T09:00:00-05:00\",", "");
		String withoutOffset = base.replace("2026-11-08T09:00:00-05:00", "2026-11-08T09:00:00");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(missing))
			.withMessage("occurred_at es obligatorio");
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(withoutOffset))
			.withMessage("occurred_at no es una fecha y hora ISO 8601 con desfase");
	}

	@Test
	void should_reject_a_missing_source() {
		String body = envelope("driver_deactivated_event", " ", "{\"driver_id\": \"" + DRIVER_ID + "\"}");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(body))
			.withMessage("source es obligatorio");
	}

	@Test
	void should_reject_a_missing_or_too_long_label() {
		String withoutName = envelope("driver_activated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}");
		String longPlate = envelope("vehicle_created_event", "vehicle",
				"{\"vehicle_id\": \"" + VEHICLE_ID + "\", \"license_plate\": \"ABCDEFGHIJK\"}");
		String longName = envelope("driver_activated_event", "driver",
				"{\"driver_id\": \"" + DRIVER_ID + "\", \"full_name\": \"" + "ñ".repeat(151) + "\"}");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(withoutName))
			.withMessage("full_name es obligatorio");
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(longPlate))
			.withMessage("license_plate supera 10 caracteres");
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class).isThrownBy(() -> this.mapper.map(longName))
			.withMessage("full_name supera 150 caracteres");
	}

	@Test
	void should_accept_a_label_at_its_maximum_length() {
		String body = envelope("driver_activated_event", "driver",
				"{\"driver_id\": \"" + DRIVER_ID + "\", \"full_name\": \"" + "ñ".repeat(150) + "\"}");

		assertThat(this.mapper.map(body).event().label()).hasSize(150);
	}

	@Test
	void should_require_a_json_mapper() {
		assertThatNullPointerException().isThrownBy(() -> new OwnerEventMessageMapper(null)).withMessage("jsonMapper");
	}

	private static String envelope(String eventType, String source, String data) {
		return """
				{"event_id": "%s", "event_type": "%s", "occurred_at": "2026-11-08T09:00:00-05:00", "source": "%s", \
				"correlation_id": "%s", "data": %s}""".formatted(EVENT_ID, eventType, source, CORRELATION_ID, data);
	}

}
