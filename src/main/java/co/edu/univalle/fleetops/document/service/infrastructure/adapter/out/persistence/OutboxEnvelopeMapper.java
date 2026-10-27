package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpired;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentRegistered;
import co.edu.univalle.fleetops.document.service.domain.event.DomainEvent;

/**
 * Arma el sobre estándar de los eventos (DAS sec. 8, confirmado el 26-10-2026): {@code event_id},
 * {@code event_type}, {@code occurred_at}, {@code source}, {@code correlation_id} y {@code data}. El
 * {@code data} de cada evento sigue la hoja «Eventos SNS» de {@code docs/G01_Endpoints_Documentacion.xlsx} y nunca
 * incluye document_number (DAS sec. 4.2).
 *
 * <p>Las claves se escriben aquí en snake_case, de forma explícita, para no depender de la estrategia de nombres
 * de Jackson configurada para la API.
 */
final class OutboxEnvelopeMapper {

	/**
	 * Tópico SNS del servicio (ADR-DOC-14). Es el nombre del contrato, no un valor del ambiente.
	 */
	static final String TOPIC = "fleetops_documents_events";

	static final String SOURCE = "fleetops-document-service";

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

	static Map<String, Object> envelope(UUID eventId, DomainEvent event, UUID correlationId) {
		Objects.requireNonNull(eventId, "eventId");
		Objects.requireNonNull(event, "event");
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("event_id", eventId.toString());
		envelope.put("event_type", eventTypeOf(event));
		envelope.put("occurred_at", event.occurredAt().toString());
		envelope.put("source", SOURCE);
		envelope.put("correlation_id", (correlationId != null) ? correlationId.toString() : null);
		envelope.put("data", dataOf(event));
		return envelope;
	}

	private static Map<String, Object> dataOf(DomainEvent event) {
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
				data.put("created_at", registered.occurredAt().toString());
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
