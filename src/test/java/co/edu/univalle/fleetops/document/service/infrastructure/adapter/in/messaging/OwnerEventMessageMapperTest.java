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

	private static final String DRIVER_ACTIVATED = "driver_activated_event";

	private static final String DRIVER_DEACTIVATED = "driver_deactivated_event";

	private static final String VEHICLE_CREATED = "vehicle_created_event";

	private static final String VEHICLE_DELETED = "vehicle_deleted_event";

	/**
	 * Cuerpo definitivo del alta de vehículos (Vehículos, 15-11-2026). Su event_type del cuerpo es VEHICLE_CREATED;
	 * el Message Attribute es vehicle_created_event.
	 */
	private static final String VEHICLE_CREATED_BODY = """
			{"event_id": "9b1d3c2e-7a4f-4e8b-a1c2-3d4e5f6a7b8c", "event_type": "VEHICLE_CREATED",
			 "timestamp": "2026-11-08T14:43:00Z",
			 "data": {"vehicle_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "plate": "ABC-123",
			          "vehicle_type": "Camion", "brand": "Chevrolet", "model": "NHR", "year": 2022,
			          "color": "Blanco", "notes": "En perfecto estado operacional", "odometer_km": 45000,
			          "status": "Activo"}}""";

	/**
	 * Cuerpo definitivo de la baja de vehículos (Vehículos, 15-11-2026).
	 */
	private static final String VEHICLE_DELETED_BODY = """
			{"event_id": "7d2e9f10-4b6a-4c8e-9a1b-2c3d4e5f6a7b", "event_type": "vehicle_deleted_event",
			 "occurred_at": "2026-10-27T10:30:00-05:00", "source": "vehicle",
			 "correlation_id": "6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e",
			 "data": {"vehicle_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "plate": "ABC123",
			          "deleted_at": "2026-10-27T10:30:00-05:00", "version_number": 9}}""";

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
		OwnerEventMessage message = this.mapper.map(DRIVER_ACTIVATED, body);

		// Assert
		OwnerReplicaEvent event = message.event();
		assertThat(event.eventId()).isEqualTo(UUID.fromString(EVENT_ID));
		assertThat(event.eventType()).isEqualTo(DRIVER_ACTIVATED);
		assertThat(event.source()).isEqualTo("driver");
		// -05:00 se compara como instante (criterio 4)
		assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-08T14:00:00Z"));
		assertThat(event.owner()).isEqualTo(new OwnerRef(OwnerType.DRIVER, UUID.fromString(DRIVER_ID)));
		assertThat(event.action()).isEqualTo(OwnerReplicaAction.ACTIVATE);
		assertThat(event.label()).isEqualTo("Ana María Restrepo");
		assertThat(message.correlationId()).isEqualTo(CORRELATION_ID);
	}

	@Test
	void should_map_the_assumed_driver_deactivated_event() {
		OwnerEventMessage message = this.mapper.map(DRIVER_DEACTIVATED,
				envelope("driver_deactivated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}"));

		assertThat(message.event().action()).isEqualTo(OwnerReplicaAction.DEACTIVATE);
		assertThat(message.event().owner().ownerType()).isEqualTo(OwnerType.DRIVER);
		assertThat(message.event().label()).isNull();
	}

	@Test
	void should_map_the_final_vehicle_created_body() {
		// Act
		OwnerEventMessage message = this.mapper.map(VEHICLE_CREATED, VEHICLE_CREATED_BODY);

		// Assert: el tipo viene del Message Attribute; el instante, de timestamp; source, del slug del productor
		OwnerReplicaEvent event = message.event();
		assertThat(event.eventType()).isEqualTo(VEHICLE_CREATED);
		assertThat(event.source()).isEqualTo("vehicle");
		assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-08T14:43:00Z"));
		assertThat(event.owner()).isEqualTo(
				new OwnerRef(OwnerType.VEHICLE, UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")));
		assertThat(event.action()).isEqualTo(OwnerReplicaAction.ACTIVATE);
		assertThat(event.label()).isEqualTo("ABC-123");
		assertThat(message.correlationId()).isNull();
	}

	@Test
	void should_map_the_final_vehicle_deleted_body() {
		// Act
		OwnerEventMessage message = this.mapper.map(VEHICLE_DELETED, VEHICLE_DELETED_BODY);

		// Assert
		OwnerReplicaEvent event = message.event();
		assertThat(event.eventType()).isEqualTo(VEHICLE_DELETED);
		assertThat(event.source()).isEqualTo("vehicle");
		assertThat(event.owner()).isEqualTo(
				new OwnerRef(OwnerType.VEHICLE, UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6")));
		assertThat(event.action()).isEqualTo(OwnerReplicaAction.DEACTIVATE);
		assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-27T15:30:00Z"));
		assertThat(event.label()).isNull();
		assertThat(message.correlationId()).isEqualTo("6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");
	}

	@Test
	void should_require_the_instant_field_of_each_event() {
		// El alta de vehículos trae el instante en timestamp; un occurred_at no lo reemplaza
		String createdWithOccurredAt = VEHICLE_CREATED_BODY.replace("\"timestamp\"", "\"occurred_at\"");
		String deletedWithTimestamp = VEHICLE_DELETED_BODY.replace("\"occurred_at\"", "\"timestamp\"");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(VEHICLE_CREATED, createdWithOccurredAt))
			.withMessage("timestamp es obligatorio");
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(VEHICLE_DELETED, deletedWithTimestamp))
			.withMessage("occurred_at es obligatorio");
	}

	@Test
	void should_accept_occurred_at_in_utc() {
		String body = envelope("driver_deactivated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}")
			.replace("2026-11-08T09:00:00-05:00", "2026-11-08T14:00:00Z");

		assertThat(this.mapper.map(DRIVER_DEACTIVATED, body).event().occurredAt())
			.isEqualTo(Instant.parse("2026-11-08T14:00:00Z"));
	}

	@Test
	void should_drop_a_missing_or_malformed_correlation_id() {
		String withoutCorrelation = envelope("driver_deactivated_event", "driver",
				"{\"driver_id\": \"" + DRIVER_ID + "\"}")
			.replace("\"correlation_id\": \"" + CORRELATION_ID + "\",", "");
		String malformed = envelope("driver_deactivated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}")
			.replace(CORRELATION_ID, "no-es-un-uuid");

		assertThat(this.mapper.map(DRIVER_DEACTIVATED, withoutCorrelation).correlationId()).isNull();
		assertThat(this.mapper.map(DRIVER_DEACTIVATED, malformed).correlationId()).isNull();
	}

	@Test
	void should_take_the_event_type_from_the_message_attribute_and_not_from_the_body() {
		// El cuerpo dice driver_deactivated_event, pero el Message Attribute (el que filtra la suscripción) manda
		String body = envelope("driver_deactivated_event", "driver",
				"{\"driver_id\": \"" + DRIVER_ID + "\", \"full_name\": \"Ana\"}");

		OwnerReplicaEvent event = this.mapper.map(DRIVER_ACTIVATED, body).event();

		assertThat(event.eventType()).isEqualTo(DRIVER_ACTIVATED);
		assertThat(event.action()).isEqualTo(OwnerReplicaAction.ACTIVATE);
	}

	@Test
	void should_reject_a_missing_or_unsupported_message_attribute() {
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(null, VEHICLE_CREATED_BODY))
			.withMessage("Message Attribute event_type ausente o no soportado: null")
			.satisfies(ex -> assertThat(ex.eventId()).isEqualTo("9b1d3c2e-7a4f-4e8b-a1c2-3d4e5f6a7b8c"));
		// El valor del cuerpo no es un valor válido del Message Attribute
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map("VEHICLE_CREATED", VEHICLE_CREATED_BODY))
			.withMessage("Message Attribute event_type ausente o no soportado: VEHICLE_CREATED");
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map("vehicle_decommissioned_event", VEHICLE_DELETED_BODY))
			.withMessage("Message Attribute event_type ausente o no soportado: vehicle_decommissioned_event");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "{roto", "[1, 2]", "\"texto\"" })
	void should_reject_a_body_that_is_not_a_json_object(String body) {
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_ACTIVATED, body))
			.satisfies(ex -> assertThat(ex.eventId()).isNull());
	}

	@Test
	void should_reject_a_null_body() {
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_ACTIVATED, null))
			.withMessage("el mensaje está vacío");
	}

	@Test
	void should_reject_a_message_without_event_id() {
		String body = """
				{"event_type": "driver_activated_event", "occurred_at": "2026-11-08T09:00:00-05:00",
				 "source": "driver", "data": {"driver_id": "%s", "full_name": "Ana"}}""".formatted(DRIVER_ID);

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_ACTIVATED, body))
			.withMessage("event_id es obligatorio")
			.satisfies(ex -> assertThat(ex.eventId()).isNull());
	}

	@Test
	void should_reject_an_event_id_that_is_not_a_uuid() {
		String body = envelope("driver_deactivated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}")
			.replace(EVENT_ID, "evt-1");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_DEACTIVATED, body))
			.withMessage("event_id no es un UUID válido")
			.satisfies(ex -> assertThat(ex.eventId()).isEqualTo("evt-1"));
	}

	@Test
	void should_reject_a_message_without_the_entity_id() {
		String body = envelope("driver_activated_event", "driver", "{\"full_name\": \"Ana\"}");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_ACTIVATED, body))
			.withMessage("data.driver_id es obligatorio");
	}

	@Test
	void should_reject_an_entity_id_that_is_not_a_uuid() {
		String notText = envelope("vehicle_deleted_event", "vehicle", "{\"vehicle_id\": 42}");
		String notUuid = envelope("vehicle_deleted_event", "vehicle", "{\"vehicle_id\": \"v-42\"}");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(VEHICLE_DELETED, notText))
			.withMessage("data.vehicle_id es obligatorio");
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(VEHICLE_DELETED, notUuid))
			.withMessage("data.vehicle_id no es un UUID válido");
	}

	@Test
	void should_reject_data_that_is_not_an_object() {
		String body = envelope("driver_deactivated_event", "driver", "\"" + DRIVER_ID + "\"");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_DEACTIVATED, body))
			.withMessage("data debe ser un objeto JSON");
	}

	@Test
	void should_reject_a_missing_or_invalid_occurred_at() {
		String base = envelope("driver_deactivated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}");
		String missing = base.replace("\"occurred_at\": \"2026-11-08T09:00:00-05:00\",", "");
		String withoutOffset = base.replace("2026-11-08T09:00:00-05:00", "2026-11-08T09:00:00");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_DEACTIVATED, missing))
			.withMessage("occurred_at es obligatorio");
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_DEACTIVATED, withoutOffset))
			.withMessage("occurred_at no es una fecha y hora ISO 8601 con desfase");
	}

	@Test
	void should_use_the_producer_slug_when_source_is_missing_or_blank() {
		String blank = envelope("driver_deactivated_event", " ", "{\"driver_id\": \"" + DRIVER_ID + "\"}");
		String missing = blank.replace("\"source\": \" \",", "");

		assertThat(this.mapper.map(DRIVER_DEACTIVATED, blank).event().source()).isEqualTo("driver");
		assertThat(this.mapper.map(DRIVER_DEACTIVATED, missing).event().source()).isEqualTo("driver");
	}

	@Test
	void should_reject_a_too_long_source() {
		String body = envelope("driver_deactivated_event", "d".repeat(151), "{\"driver_id\": \"" + DRIVER_ID + "\"}");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_DEACTIVATED, body))
			.withMessage("source supera 150 caracteres");
	}

	@Test
	void should_reject_a_missing_or_too_long_label() {
		String withoutName = envelope("driver_activated_event", "driver", "{\"driver_id\": \"" + DRIVER_ID + "\"}");
		String longPlate = VEHICLE_CREATED_BODY.replace("ABC-123", "ABCDEFGHIJK");
		String longName = envelope("driver_activated_event", "driver",
				"{\"driver_id\": \"" + DRIVER_ID + "\", \"full_name\": \"" + "ñ".repeat(151) + "\"}");

		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_ACTIVATED, withoutName))
			.withMessage("full_name es obligatorio");
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(VEHICLE_CREATED, longPlate))
			.withMessage("plate supera 10 caracteres");
		assertThatExceptionOfType(InvalidOwnerEventMessageException.class)
			.isThrownBy(() -> this.mapper.map(DRIVER_ACTIVATED, longName))
			.withMessage("full_name supera 150 caracteres");
	}

	@Test
	void should_accept_a_label_at_its_maximum_length() {
		String body = envelope("driver_activated_event", "driver",
				"{\"driver_id\": \"" + DRIVER_ID + "\", \"full_name\": \"" + "ñ".repeat(150) + "\"}");

		assertThat(this.mapper.map(DRIVER_ACTIVATED, body).event().label()).hasSize(150);
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
