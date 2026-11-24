
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentPersistenceMapperHistoryTest {

    @Test
    void should_restore_historical_fields_from_jpa_entity() {

        // Arrange
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID previousDocumentId = UUID.randomUUID();

        Instant createdAt =
                Instant.parse("2026-01-01T15:00:00Z");

        Instant cancelledAt =
                Instant.parse("2026-11-20T15:00:00Z");

        DocumentJpaEntity entity = new DocumentJpaEntity(
                documentId,
                "SOAT",
                "VEHICLE",
                ownerId,
                "SOAT-12345",
                "Aseguradora Ejemplo",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                "CANCELLED",
                null,
                createdAt,
                cancelledAt);

        ReflectionTestUtils.setField(
                entity, "previousDocumentId", previousDocumentId);

        ReflectionTestUtils.setField(
                entity, "cancellationReason", "Documento anulado");

        ReflectionTestUtils.setField(
                entity, "cancelledAt", cancelledAt);

        // Act
        Document document = DocumentPersistenceMapper.toDomain(entity);

        // Assert
        assertThat(document.id()).isEqualTo(documentId);
        assertThat(document.status()).isEqualTo(DocumentStatus.CANCELLED);

        assertThat(document.previousDocumentId())
                .isEqualTo(previousDocumentId);

        assertThat(document.cancellationReason())
                .isEqualTo("Documento anulado");

        assertThat(document.cancelledAt())
                .isEqualTo(cancelledAt);

        assertThat(document.createdAt())
                .isEqualTo(createdAt);

        assertThat(document.updatedAt())
                .isEqualTo(cancelledAt);
    }
}