package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DocumentJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentPersistenceAdapterCancellationTest {

	private static final UUID DOCUMENT_ID = UUID.randomUUID();

	private static final UUID OWNER_ID = UUID.randomUUID();

	private static final UUID PREVIOUS_DOCUMENT_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-10-26T15:00:00Z");

	private static final Instant CANCELLED_AT = Instant.parse("2026-11-27T15:00:00Z");

	private static final String REASON = "Documento registrado por error";

	@Mock
	private DocumentJpaRepository repository;

	@InjectMocks
	private DocumentPersistenceAdapter adapter;

	@Test
	void should_find_and_map_a_document_for_update() {
		// Arrange
		DocumentJpaEntity entity = entity();

		given(this.repository.findForUpdate(DOCUMENT_ID))
				.willReturn(Optional.of(entity));

		// Act
		Optional<Document> result = this.adapter.findByIdForUpdate(DOCUMENT_ID);

		// Assert
		assertThat(result).isPresent();

		Document document = result.orElseThrow();

		assertThat(document.id()).isEqualTo(DOCUMENT_ID);
		assertThat(document.documentType()).isEqualTo(DocumentTypeCode.SOAT);
		assertThat(document.status()).isEqualTo(DocumentStatus.VALID);
		assertThat(document.previousDocumentId()).isEqualTo(PREVIOUS_DOCUMENT_ID);

		verify(this.repository).findForUpdate(DOCUMENT_ID);
		verifyNoMoreInteractions(this.repository);
	}

	@Test
	void should_return_empty_when_document_for_update_does_not_exist() {
		// Arrange
		given(this.repository.findForUpdate(DOCUMENT_ID))
				.willReturn(Optional.empty());

		// Act
		Optional<Document> result = this.adapter.findByIdForUpdate(DOCUMENT_ID);

		// Assert
		assertThat(result).isEmpty();

		verify(this.repository).findForUpdate(DOCUMENT_ID);
	}

	@Test
	void should_update_cancellation_without_inserting_a_new_document() {
		// Arrange
		DocumentJpaEntity entity = entity();

		Document original = DocumentPersistenceMapper.toDomain(entity);

		Document cancelled = original.cancel(REASON, CANCELLED_AT);

		given(this.repository.findById(DOCUMENT_ID))
				.willReturn(Optional.of(entity));

		// Act
		this.adapter.updateCancellation(cancelled);

		// Assert
		assertThat(entity.getStatus()).isEqualTo("CANCELLED");
		assertThat(entity.getCancellationReason()).isEqualTo(REASON);
		assertThat(entity.getCancelledAt()).isEqualTo(CANCELLED_AT);
		assertThat(entity.getUpdatedAt()).isEqualTo(CANCELLED_AT);

		assertThat(entity.getDocumentId()).isEqualTo(DOCUMENT_ID);
		assertThat(entity.getDocumentType()).isEqualTo("SOAT");
		assertThat(entity.getOwnerType()).isEqualTo("VEHICLE");
		assertThat(entity.getOwnerId()).isEqualTo(OWNER_ID);
		assertThat(entity.getDocumentNumber()).isEqualTo("SOAT-12345");
		assertThat(entity.getIssuingEntity()).isEqualTo("Aseguradora Ejemplo");
		assertThat(entity.getIssueDate()).isEqualTo(LocalDate.of(2026, 10, 26));
		assertThat(entity.getExpirationDate()).isEqualTo(LocalDate.of(2027, 10, 26));
		assertThat(entity.getLicenseCategory()).isNull();
		assertThat(entity.getCreatedAt()).isEqualTo(CREATED);
		assertThat(entity.getPreviousDocumentId()).isEqualTo(PREVIOUS_DOCUMENT_ID);
		assertThat(entity.getVersion()).isEqualTo(3);

		verify(this.repository).findById(DOCUMENT_ID);
		verifyNoMoreInteractions(this.repository);
	}

	@Test
	void should_fail_to_update_cancellation_of_a_missing_document() {
		// Arrange
		Document original = DocumentPersistenceMapper.toDomain(entity());

		Document cancelled = original.cancel(REASON, CANCELLED_AT);

		given(this.repository.findById(DOCUMENT_ID))
				.willReturn(Optional.empty());

		// Act and Assert
		assertThatIllegalStateException()
				.isThrownBy(() -> this.adapter.updateCancellation(cancelled))
				.withMessageContaining(DOCUMENT_ID.toString());

		verify(this.repository).findById(DOCUMENT_ID);
		verifyNoMoreInteractions(this.repository);
	}

	private static DocumentJpaEntity entity() {
		DocumentJpaEntity entity = new DocumentJpaEntity(
				DOCUMENT_ID,
				"SOAT",
				"VEHICLE",
				OWNER_ID,
				"SOAT-12345",
				"Aseguradora Ejemplo",
				LocalDate.of(2026, 10, 26),
				LocalDate.of(2027, 10, 26),
				"VALID",
				null,
				CREATED,
				CREATED);

		ReflectionTestUtils.setField(entity, "previousDocumentId", PREVIOUS_DOCUMENT_ID);
		ReflectionTestUtils.setField(entity, "version", 3);

		return entity;
	}

}
