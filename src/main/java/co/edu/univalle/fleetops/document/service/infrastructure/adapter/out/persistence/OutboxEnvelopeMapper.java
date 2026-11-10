package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpired;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentRegistered;
import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;
import co.edu.univalle.fleetops.document.service.infrastructure.config.TimestampFormat;

/**
 * Arma el sobre estándar de los eventos (DAS sec. 8, confirmado el 26-10-2026): {@code event_id},
 * {@code event_type}, {@code occurred_at}, {@code source}, {@code correlation_id} y {@code data}. El
 * {@code data} de cada evento sigue la hoja «Eventos SNS» de {@code docs/G01_Endpoints_Documentacion.xlsx} y nunca
 * incluye document_number (DAS sec. 4.2).
 *
 * <p>Las claves se escriben aquí en snake_case, de forma explícita, para no depender de la estrategia de nombres
 * de Jackson configurada para la API. Las marcas de tiempo salen en la zona de negocio, con su desfase
 * (America/Bogota, -05:00; decisión P-02 del 08-11-2026).
 */
final class OutboxEnvelopeMapper {

	/**
	 * Tópico SNS del servicio: {@code fleetops_{slug}_events} con el slug en singular (ADR-DOC-14, decisión P-01
	 * del 08-11-2026). Es el nombre del contrato, no un valor del ambiente.
	 */
	static final String TOPIC = "fleetops_document_events";

	/**
	 * Slug del microservicio que publica (decisión P-03 del 08-11-2026).
	 */
	static final String SOURCE = "document";

	private OutboxEnvelopeMapper() {
	}

	/**
	 * Valor del Message Attribute {@code event_type} (ADR-DOC-14).
	 */
	static String eventTypeOf(DomainEvent event) {
		return switch (event) {
			case DocumentRegistered registered -> "document_registered_event";
			case DocumentExpired expired -> "document_expired_event";
		};
	}

	static Map<String, Object> envelope(UUID eventId, DomainEvent event, UUID correlationId, ZoneId zone) {
		Objects.requireNonNull(eventId, "eventId");
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(zone, "zone");
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("event_id", eventId.toString());
		envelope.put("event_type", eventTypeOf(event));
		envelope.put("occurred_at", TimestampFormat.format(event.occurredAt(), zone));
		envelope.put("source", SOURCE);
		envelope.put("correlation_id", (correlationId != null) ? correlationId.toString() : null);
		envelope.put("data", dataOf(event, zone));
		return envelope;
	}

	private static Map<String, Object> dataOf(DomainEvent event, ZoneId zone) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("document_id", event.documentId().toString());
		switch (event) {
			case DocumentRegistered registered -> {
				data.put("document_type", registered.documentType().name());
				data.put("owner_type", registered.owner().ownerType().name());
				data.put("owner_id", registered.owner().ownerId().toString());
				data.put("issuing_entity", registered.issuingEntity());
				data.put("issue_date", registered.validity().issueDate().toString());
				data.put("expiration_date", textOrNull(registered.validity().expirationDate()));
				data.put("license_category",
						(registered.licenseCategory() != null) ? registered.licenseCategory().value() : null);
				data.put("status", registered.status().name());
				data.put("created_at", TimestampFormat.format(registered.occurredAt(), zone));
			}
			case DocumentExpired expired -> {
				data.put("document_type", expired.documentType().name());
				data.put("owner_type", expired.owner().ownerType().name());
				data.put("owner_id", expired.owner().ownerId().toString());
				data.put("expiration_date", expired.expirationDate().toString());
			}
		}
		return data;
	}

	private static String textOrNull(Object value) {
		return (value != null) ? value.toString() : null;
	}

}
