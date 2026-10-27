package co.edu.univalle.fleetops.document.service.application.port.in;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentRegistration;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeRules;
import co.edu.univalle.fleetops.document.service.domain.model.LicenseCategory;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.model.ValidityPeriod;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;
import co.edu.univalle.fleetops.document.service.domain.service.StatusEvaluation;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentViewTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

	private static final Instant NOW = Instant.parse("2026-10-26T15:04:05Z");

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID VEHICLE_ID = UUID.fromString("01960000-0000-7000-8000-000000000001");

	private static final UUID DRIVER_ID = UUID.fromString("01960000-0000-7000-8000-000000000101");

	private final DocumentStatusCalculator calculator = new DocumentStatusCalculator(30);

	@Test
	void should_expose_a_property_card_without_category_or_days_remaining() {
		// Arrange
		Document document = Document.register(
				new DocumentRegistration(DOCUMENT_ID, new OwnerRef(OwnerType.VEHICLE, VEHICLE_ID),
						new DocumentNumber("TP-1"), "Tránsito", new ValidityPeriod(LocalDate.of(2020, 5, 10), null),
						null),
				new DocumentTypeRules(DocumentTypeCode.PROPERTY_CARD, OwnerType.VEHICLE, false, true, false, true),
				this.calculator, TODAY, NOW);

		// Act
		DocumentView view = DocumentView.of(document, new StatusEvaluation(DocumentStatus.VALID, null));

		// Assert
		assertThat(view).isEqualTo(new DocumentView(DOCUMENT_ID, DocumentTypeCode.PROPERTY_CARD, OwnerType.VEHICLE,
				VEHICLE_ID, "TP-1", "Tránsito", LocalDate.of(2020, 5, 10), null, null, DocumentStatus.VALID, null, NOW,
				NOW));
	}

	@Test
	void should_expose_the_license_category_and_the_evaluated_status() {
		// Arrange
		Document document = Document.register(
				new DocumentRegistration(DOCUMENT_ID, new OwnerRef(OwnerType.DRIVER, DRIVER_ID),
						new DocumentNumber("LIC-1"), "Tránsito",
						new ValidityPeriod(LocalDate.of(2024, 3, 1), LocalDate.of(2034, 3, 1)),
						new LicenseCategory("C1")),
				new DocumentTypeRules(DocumentTypeCode.DRIVER_LICENSE, OwnerType.DRIVER, true, true, true, true),
				this.calculator, TODAY, NOW);

		// Act
		DocumentView view = DocumentView.of(document, new StatusEvaluation(DocumentStatus.EXPIRING_SOON, 5));

		// Assert
		assertThat(view.licenseCategory()).isEqualTo("C1");
		assertThat(view.status()).isEqualTo(DocumentStatus.EXPIRING_SOON);
		assertThat(view.daysRemaining()).isEqualTo(5);
	}

}
