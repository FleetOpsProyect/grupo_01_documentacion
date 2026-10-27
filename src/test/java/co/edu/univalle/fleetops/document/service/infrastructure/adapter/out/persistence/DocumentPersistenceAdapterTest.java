package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentRegistration;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeRules;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.model.ValidityPeriod;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DocumentJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentPersistenceAdapterTest {

	private static final OwnerRef VEHICLE = new OwnerRef(OwnerType.VEHICLE,
			UUID.fromString("01960000-0000-7000-8000-000000000001"));

	@Mock
	private DocumentJpaRepository repository;

	@InjectMocks
	private DocumentPersistenceAdapter adapter;

	@Test
	void should_look_for_active_documents_only_in_valid_expiring_soon_and_expired() {
		// Act
		this.adapter.existsActiveByOwnerAndType(VEHICLE, DocumentTypeCode.SOAT);

		// Assert (ADR-DOC-11)
		verify(this.repository).existsByOwnerTypeAndOwnerIdAndDocumentTypeAndStatusIn("VEHICLE", VEHICLE.ownerId(),
				"SOAT", List.of("VALID", "EXPIRING_SOON", "EXPIRED"));
	}

	@Test
	void should_translate_the_type_number_unique_constraint_to_a_conflict() {
		// Arrange
		given(this.repository.saveAndFlush(any())).willThrow(violation("uk_documents_type_number"));

		// Act + Assert
		assertThatExceptionOfType(DomainConflictException.class).isThrownBy(() -> this.adapter.save(soat()))
			.withMessage("Ya existe un documento SOAT con ese document_number");
	}

	@Test
	void should_translate_the_one_active_per_owner_constraint_to_a_conflict() {
		// Arrange
		given(this.repository.saveAndFlush(any())).willThrow(violation("uk_documents_one_active_per_owner_type"));

		// Act + Assert
		assertThatExceptionOfType(DomainConflictException.class).isThrownBy(() -> this.adapter.save(soat()))
			.withMessage("El propietario ya tiene un documento SOAT activo. Para cambiarlo use la renovación");
	}

	@Test
	void should_rethrow_any_other_integrity_violation() {
		// Arrange
		DataIntegrityViolationException other = violation("ck_documents_license_category");
		given(this.repository.saveAndFlush(any())).willThrow(other);

		// Act + Assert
		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> this.adapter.save(soat()))
			.isSameAs(other);
	}

	@Test
	void should_rethrow_an_integrity_violation_without_constraint_name() {
		// Arrange
		DataIntegrityViolationException other = new DataIntegrityViolationException("sin causa");
		given(this.repository.saveAndFlush(any())).willThrow(other);

		// Act + Assert
		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> this.adapter.save(soat()))
			.isSameAs(other);
	}

	@Test
	void should_store_the_document_columns_as_a_new_row() {
		// Arrange
		Document document = soat();

		// Act
		this.adapter.save(document);

		// Assert
		ArgumentCaptor<DocumentJpaEntity> saved = ArgumentCaptor.forClass(DocumentJpaEntity.class);
		verify(this.repository).saveAndFlush(saved.capture());
		DocumentJpaEntity entity = saved.getValue();
		assertThat(entity.getDocumentId()).isEqualTo(document.id());
		assertThat(entity.getDocumentType()).isEqualTo("SOAT");
		assertThat(entity.getOwnerType()).isEqualTo("VEHICLE");
		assertThat(entity.getDocumentNumber()).isEqualTo("AT-1");
		assertThat(entity.getStatus()).isEqualTo("VALID");
		assertThat(entity.getLicenseCategory()).isNull();
		// Sin versión, Spring Data lo trata como nuevo e inserta (ADR-DOC-04)
		assertThat(entity.getVersion()).isNull();
	}

	private static DataIntegrityViolationException violation(String constraint) {
		return new DataIntegrityViolationException("violación",
				new ConstraintViolationException("violación", new SQLException("23505"), constraint));
	}

	private static Document soat() {
		return Document.register(
				new DocumentRegistration(UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f"), VEHICLE,
						new DocumentNumber("AT-1"), "Seguros Demo S.A.",
						new ValidityPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30)), null),
				new DocumentTypeRules(DocumentTypeCode.SOAT, OwnerType.VEHICLE, true, true, true, true),
				new DocumentStatusCalculator(30), LocalDate.of(2026, 10, 26), Instant.parse("2026-10-26T15:04:05Z"));
	}

}
