package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository.ExpirationCandidate;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DocumentJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;

/**
 * Métodos de {@link DocumentPersistenceAdapter} que usa el job de vigencia (HU-DOC-06).
 */
@ExtendWith(MockitoExtension.class)
class DocumentPersistenceAdapterExpirationTest {

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID DRIVER_ID = UUID.fromString("01960000-0000-7000-8000-000000000101");

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

	private static final Instant CREATED = Instant.parse("2026-10-26T15:04:05Z");

	@Mock
	private DocumentJpaRepository repository;

	@InjectMocks
	private DocumentPersistenceAdapter adapter;

	private static DocumentJpaEntity license(String status, LocalDate expirationDate) {
		return new DocumentJpaEntity(DOCUMENT_ID, "DRIVER_LICENSE", "DRIVER", DRIVER_ID, "LIC-12345",
				"Secretaría de Movilidad de Cali", LocalDate.of(2020, 1, 10), expirationDate, status, "C1", CREATED,
				CREATED);
	}

	@Test
	void should_delegate_the_candidate_query_with_the_alert_horizon() {
		given(this.repository.findExpirationCandidateIds(TODAY, TODAY.plusDays(30))).willReturn(List.of(DOCUMENT_ID));

		assertThat(this.adapter.findExpirationCandidateIds(TODAY, TODAY.plusDays(30))).containsExactly(DOCUMENT_ID);
	}

	@ParameterizedTest
	@ValueSource(strings = { "VALID", "EXPIRING_SOON" })
	void should_lock_an_active_document_with_expiration(String status) {
		// Arrange
		given(this.repository.findForUpdate(DOCUMENT_ID)).willReturn(Optional.of(license(status, TODAY)));

		// Act
		Optional<ExpirationCandidate> candidate = this.adapter.lockExpirationCandidate(DOCUMENT_ID);

		// Assert
		assertThat(candidate).contains(new ExpirationCandidate(DOCUMENT_ID, DocumentTypeCode.DRIVER_LICENSE,
				new OwnerRef(OwnerType.DRIVER, DRIVER_ID), TODAY, DocumentStatus.valueOf(status)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "EXPIRED", "REPLACED", "CANCELLED" })
	void should_not_return_a_document_that_is_no_longer_a_candidate(String status) {
		given(this.repository.findForUpdate(DOCUMENT_ID)).willReturn(Optional.of(license(status, TODAY)));

		assertThat(this.adapter.lockExpirationCandidate(DOCUMENT_ID)).isEmpty();
	}

	@Test
	void should_not_return_a_document_without_expiration_or_missing() {
		given(this.repository.findForUpdate(DOCUMENT_ID)).willReturn(Optional.of(license("VALID", null)));
		assertThat(this.adapter.lockExpirationCandidate(DOCUMENT_ID)).isEmpty();

		given(this.repository.findForUpdate(DOCUMENT_ID)).willReturn(Optional.empty());
		assertThat(this.adapter.lockExpirationCandidate(DOCUMENT_ID)).isEmpty();
	}

	@Test
	void should_change_the_status_and_the_update_timestamp() {
		// Arrange
		DocumentJpaEntity entity = license("VALID", TODAY.plusDays(30));
		given(this.repository.findById(DOCUMENT_ID)).willReturn(Optional.of(entity));
		Instant now = Instant.parse("2026-11-23T06:00:00Z");

		// Act
		this.adapter.updateStatus(DOCUMENT_ID, DocumentStatus.EXPIRING_SOON, now);

		// Assert
		assertThat(entity.getStatus()).isEqualTo("EXPIRING_SOON");
		assertThat(entity.getUpdatedAt()).isEqualTo(now);
	}

	@Test
	void should_fail_to_change_the_status_of_a_missing_document() {
		given(this.repository.findById(DOCUMENT_ID)).willReturn(Optional.empty());

		assertThatIllegalStateException()
			.isThrownBy(() -> this.adapter.updateStatus(DOCUMENT_ID, DocumentStatus.VALID, CREATED))
			.withMessageContaining(DOCUMENT_ID.toString());
	}

}
