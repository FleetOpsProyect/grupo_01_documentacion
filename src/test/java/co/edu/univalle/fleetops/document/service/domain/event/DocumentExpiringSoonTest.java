package co.edu.univalle.fleetops.document.service.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DocumentExpiringSoonTest {

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final OwnerRef VEHICLE = new OwnerRef(OwnerType.VEHICLE,
			UUID.fromString("01960000-0000-7000-8000-000000000001"));

	private static final LocalDate EXPIRATION = LocalDate.of(2026, 12, 23);

	private static final Instant NOW = Instant.parse("2026-11-23T06:00:00Z");

	@Test
	void should_describe_the_threshold_reached() {
		DocumentExpiringSoon event = new DocumentExpiringSoon(DOCUMENT_ID, DocumentTypeCode.SOAT, VEHICLE,
				EXPIRATION, 30, 30, NOW);

		assertThat(event.documentId()).isEqualTo(DOCUMENT_ID);
		assertThat(event.owner()).isEqualTo(VEHICLE);
		assertThat(event.thresholdDays()).isEqualTo(30);
		assertThat(event.daysRemaining()).isEqualTo(30);
		assertThat(event.occurredAt()).isEqualTo(NOW);
	}

	@Test
	void should_reject_missing_fields() {
		assertThatNullPointerException().isThrownBy(() -> new DocumentExpiringSoon(null, DocumentTypeCode.SOAT,
				VEHICLE, EXPIRATION, 30, 30, NOW)).withMessage("documentId");
		assertThatNullPointerException().isThrownBy(
				() -> new DocumentExpiringSoon(DOCUMENT_ID, null, VEHICLE, EXPIRATION, 30, 30, NOW))
			.withMessage("documentType");
		assertThatNullPointerException().isThrownBy(() -> new DocumentExpiringSoon(DOCUMENT_ID,
				DocumentTypeCode.SOAT, null, EXPIRATION, 30, 30, NOW)).withMessage("owner");
		assertThatNullPointerException().isThrownBy(() -> new DocumentExpiringSoon(DOCUMENT_ID,
				DocumentTypeCode.SOAT, VEHICLE, null, 30, 30, NOW)).withMessage("expirationDate");
		assertThatNullPointerException().isThrownBy(() -> new DocumentExpiringSoon(DOCUMENT_ID,
				DocumentTypeCode.SOAT, VEHICLE, EXPIRATION, 30, 30, null)).withMessage("occurredAt");
	}

	@Test
	void should_reject_inconsistent_days() {
		assertThatIllegalArgumentException().isThrownBy(() -> new DocumentExpiringSoon(DOCUMENT_ID,
				DocumentTypeCode.SOAT, VEHICLE, EXPIRATION, -1, 0, NOW));
		assertThatIllegalArgumentException().isThrownBy(() -> new DocumentExpiringSoon(DOCUMENT_ID,
				DocumentTypeCode.SOAT, VEHICLE, EXPIRATION, 7, -1, NOW));
		assertThatIllegalArgumentException().isThrownBy(() -> new DocumentExpiringSoon(DOCUMENT_ID,
				DocumentTypeCode.SOAT, VEHICLE, EXPIRATION, 7, 8, NOW));
	}

}
