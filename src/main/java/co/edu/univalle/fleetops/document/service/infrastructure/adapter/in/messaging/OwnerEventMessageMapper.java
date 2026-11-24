package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaAction;
import co.edu.univalle.fleetops.document.service.application.port.in.OwnerReplicaEvent;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Anti-Corruption Layer: traduce un mensaje SQS (raw message delivery) a un {@link OwnerReplicaEvent} (HU-DOC-10,
 * DAS sec. 8.2).
 *
 * <p>El tipo de evento lo decide el Message Attribute {@code event_type}, el mismo por el que filtran las
 * suscripciones (ADR-DOC-14). El {@code event_type} del cuerpo no se usa: en el alta de vehículos vale
 * {@code VEHICLE_CREATED} y el atributo, {@code vehicle_created_event}. Los cuerpos de Vehículos y Conductores son el
 * contrato definitivo de esos equipos (decisión del 15-11-2026):
 * <ul>
 * <li>{@code driver_activated_event} (confirmado) y {@code driver_deactivated_event} (SUPUESTO): {@code occurred_at},
 * {@code source}, {@code correlation_id} y {@code data.driver_id} / {@code data.full_name}.</li>
 * <li>{@code vehicle_created_event}: el instante llega en {@code timestamp} (UTC) y no trae {@code source} ni
 * {@code correlation_id}; la placa llega en {@code data.plate}.</li>
 * <li>{@code vehicle_deleted_event}: {@code occurred_at}, {@code source}, {@code correlation_id} y
 * {@code data.vehicle_id}.</li>
 * </ul>
 * Si falta {@code source}, se usa el slug del productor ({@code vehicle} o {@code driver}). Ignora los campos
 * desconocidos del sobre y de {@code data} (criterio 5). El instante se acepta con cualquier desfase ISO 8601 y se
 * compara como instante (criterio 4). Cualquier otro mensaje es inválido.
 */
public final class OwnerEventMessageMapper {

	static final int MAX_LICENSE_PLATE_LENGTH = 10;

	static final int MAX_FULL_NAME_LENGTH = 150;

	static final int MAX_SOURCE_LENGTH = 150;

	private static final String DRIVER_SOURCE = "driver";

	private static final String VEHICLE_SOURCE = "vehicle";

	private static final String OCCURRED_AT = "occurred_at";

	private static final Map<String, SupportedEvent> SUPPORTED_EVENTS = Map.of(
			"driver_activated_event",
			new SupportedEvent(OwnerType.DRIVER, OwnerReplicaAction.ACTIVATE, DRIVER_SOURCE, OCCURRED_AT, "driver_id",
					"full_name", MAX_FULL_NAME_LENGTH),
			"driver_deactivated_event",
			new SupportedEvent(OwnerType.DRIVER, OwnerReplicaAction.DEACTIVATE, DRIVER_SOURCE, OCCURRED_AT,
					"driver_id", null, 0),
			"vehicle_created_event",
			new SupportedEvent(OwnerType.VEHICLE, OwnerReplicaAction.ACTIVATE, VEHICLE_SOURCE, "timestamp",
					"vehicle_id", "plate", MAX_LICENSE_PLATE_LENGTH),
			"vehicle_deleted_event",
			new SupportedEvent(OwnerType.VEHICLE, OwnerReplicaAction.DEACTIVATE, VEHICLE_SOURCE, OCCURRED_AT,
					"vehicle_id", null, 0));

	private final JsonMapper jsonMapper;

	public OwnerEventMessageMapper(JsonMapper jsonMapper) {
		this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
	}

	/**
	 * @param eventType valor del Message Attribute {@code event_type}, o {@code null} si el mensaje no lo trae
	 * @param body cuerpo del mensaje
	 * @throws InvalidOwnerEventMessageException si el mensaje no es un evento de réplica válido
	 */
	public OwnerEventMessage map(String eventType, String body) {
		JsonNode envelope = parse(body);
		String rawEventId = text(envelope, "event_id");
		UUID eventId = uuid(rawEventId, "event_id", rawEventId);

		SupportedEvent supported = eventType == null ? null : SUPPORTED_EVENTS.get(eventType);
		if (supported == null) {
			throw new InvalidOwnerEventMessageException(
					"Message Attribute event_type ausente o no soportado: " + eventType, rawEventId);
		}
		String source = source(envelope, supported, rawEventId);
		Instant occurredAt = instant(text(envelope, supported.occurredAtField()), supported.occurredAtField(),
				rawEventId);

		JsonNode data = envelope.path("data");
		if (!data.isObject()) {
			throw new InvalidOwnerEventMessageException("data debe ser un objeto JSON", rawEventId);
		}
		UUID ownerId = uuid(text(data, supported.idField()), "data." + supported.idField(), rawEventId);
		String label = supported.labelField() == null ? null
				: boundedText(data, supported.labelField(), supported.maxLabelLength(), rawEventId);

		OwnerReplicaEvent event = new OwnerReplicaEvent(eventId, eventType, source, occurredAt,
				new OwnerRef(supported.ownerType(), ownerId), supported.action(), label);
		return new OwnerEventMessage(event, correlationId(envelope));
	}

	private JsonNode parse(String body) {
		if (body == null || body.isBlank()) {
			throw new InvalidOwnerEventMessageException("el mensaje está vacío", null);
		}
		JsonNode envelope;
		try {
			envelope = this.jsonMapper.readTree(body);
		}
		catch (JacksonException ex) {
			throw new InvalidOwnerEventMessageException("el mensaje no es JSON válido", null, ex);
		}
		if (!envelope.isObject()) {
			throw new InvalidOwnerEventMessageException("el mensaje debe ser un objeto JSON", null);
		}
		return envelope;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isString() ? value.stringValue() : null;
	}

	private static UUID uuid(String value, String field, String rawEventId) {
		if (value == null) {
			throw new InvalidOwnerEventMessageException(field + " es obligatorio", rawEventId);
		}
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidOwnerEventMessageException(field + " no es un UUID válido", rawEventId, ex);
		}
	}

	private static String source(JsonNode envelope, SupportedEvent supported, String rawEventId) {
		String value = text(envelope, "source");
		if (value == null || value.isBlank()) {
			return supported.defaultSource();
		}
		return boundedText(envelope, "source", MAX_SOURCE_LENGTH, rawEventId);
	}

	private static Instant instant(String value, String field, String rawEventId) {
		if (value == null) {
			throw new InvalidOwnerEventMessageException(field + " es obligatorio", rawEventId);
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		}
		catch (DateTimeParseException ex) {
			throw new InvalidOwnerEventMessageException(field + " no es una fecha y hora ISO 8601 con desfase",
					rawEventId, ex);
		}
	}

	private static String boundedText(JsonNode node, String field, int maxLength, String rawEventId) {
		String value = text(node, field);
		String trimmed = value == null ? "" : value.strip();
		if (trimmed.isEmpty()) {
			throw new InvalidOwnerEventMessageException(field + " es obligatorio", rawEventId);
		}
		if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
			throw new InvalidOwnerEventMessageException(field + " supera " + maxLength + " caracteres", rawEventId);
		}
		return trimmed;
	}

	private static String correlationId(JsonNode envelope) {
		String value = text(envelope, "correlation_id");
		if (value == null) {
			return null;
		}
		try {
			return UUID.fromString(value).toString();
		}
		catch (IllegalArgumentException ex) {
			// Un correlation_id mal formado no impide aplicar el evento; solo no se enlaza en los logs
			return null;
		}
	}

	private record SupportedEvent(OwnerType ownerType, OwnerReplicaAction action, String defaultSource,
			String occurredAtField, String idField, String labelField, int maxLabelLength) {
	}

}
