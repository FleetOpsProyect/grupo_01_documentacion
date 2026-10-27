package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpired;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentRegistered;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.LicenseCategory;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.model.ValidityPeriod;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEnvelopeMapperTest {

	private static final UUID EVENT_ID = UUID.fromString("01960000-1000-7000-8000-000000000e01");

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID CORRELATION_ID = UUID.fromString("6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");

	private static final OwnerRef VEHICLE = new OwnerRef(OwnerType.VEHICLE,
			UUID.fromString("01960000-0000-7000-8000-000000000001"));

	private static final Instant NOW = Instant.parse("2026-10-26T15:04:05Z");

	@Test
	void should_build_the_standard_envelope_of_document_registered_without_document_number() {
		// Arrange
		DocumentRegistered event = new DocumentRegistered(DOCUMENT_ID, DocumentTypeCode.SOAT, VEHICLE,
				"Seguros Demo S.A.", new ValidityPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30)), null,
				DocumentStatus.VALID, NOW);

		// Act
		Map<String, Object> envelope = OutboxEnvelopeMapper.envelope(EVENT_ID, event, CORRELATION_ID);

		// Assert (sobre confirmado el 26-10-2026 y hoja «Eventos SNS»)
		assertThat(envelope).containsOnlyKeys("event_id", "event_type", "occurred_at", "source", "correlation_id",
				"data");
		assertThat(envelope).containsEntry("event_id", EVENT_ID.toString())
			.containsEntry("event_type", "document_registered_event")
			.containsEntry("occurred_at", "2026-10-26T15:04:05Z")
			.containsEntry("source", "fleetops-document-service")
			.containsEntry("correlation_id", CORRELATION_ID.toString());
		Map<String, Object> data = data(envelope);
		assertThat(data.keySet()).containsExactly("document_id", "document_type", "owner_type", "owner_id",
				"issuing_entity", "issue_date", "expiration_date", "license_category", "status", "created_at");
		assertThat(data).containsEntry("document_id", DOCUMENT_ID.toString())
			.containsEntry("document_type", "SOAT")
			.containsEntry("owner_type", "VEHICLE")
			.containsEntry("owner_id", VEHICLE.ownerId().toString())
			.containsEntry("issuing_entity", "Seguros Demo S.A.")
			.containsEntry("issue_date", "2026-10-01")
			.containsEntry("expiration_date", "2027-09-30")
			.containsEntry("license_category", null)
			.containsEntry("status", "VALID")
			.containsEntry("created_at", "2026-10-26T15:04:05Z")
			.doesNotContainKey("document_number");
	}

	@Test
	void should_include_the_license_category_and_an_empty_expiration_when_present_or_absent() {
		// Arrange
		DocumentRegistered license = new DocumentRegistered(DOCUMENT_ID, DocumentTypeCode.DRIVER_LICENSE,
				new OwnerRef(OwnerType.DRIVER, UUID.fromString("01960000-0000-7000-8000-000000000101")), "Tránsito",
				new ValidityPeriod(LocalDate.of(2024, 3, 1), LocalDate.of(2034, 3, 1)), new LicenseCategory("C1"),
				DocumentStatus.VALID, NOW);
		DocumentRegistered propertyCard = new DocumentRegistered(DOCUMENT_ID, DocumentTypeCode.PROPERTY_CARD,
				VEHICLE, "Tránsito", new ValidityPeriod(LocalDate.of(2020, 5, 10), null), null, DocumentStatus.VALID,
				NOW);

		// Act
		Map<String, Object> licenseData = data(OutboxEnvelopeMapper.envelope(EVENT_ID, license, null));
		Map<String, Object> cardData = data(OutboxEnvelopeMapper.envelope(EVENT_ID, propertyCard, null));

		// Assert
		assertThat(licenseData).containsEntry("license_category", "C1");
		assertThat(cardData).containsEntry("expiration_date", null);
	}

	@Test
	void should_build_the_envelope_of_document_expired_with_empty_correlation() {
		// Arrange
		DocumentExpired event = new DocumentExpired(DOCUMENT_ID, DocumentTypeCode.SOAT, VEHICLE,
				LocalDate.of(2026, 10, 20), NOW);

		// Act
		Map<String, Object> envelope = OutboxEnvelopeMapper.envelope(EVENT_ID, event, null);

		// Assert
		assertThat(envelope).containsEntry("event_type", "document_expired_event").containsEntry("correlation_id",
				null);
		assertThat(data(envelope)).containsExactly(Map.entry("document_id", DOCUMENT_ID.toString()),
				Map.entry("document_type", "SOAT"), Map.entry("owner_type", "VEHICLE"),
				Map.entry("owner_id", VEHICLE.ownerId().toString()), Map.entry("expiration_date", "2026-10-20"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> data(Map<String, Object> envelope) {
		return (Map<String, Object>) envelope.get("data");
	}

}
