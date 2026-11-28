package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentAuditLogJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AuditLogPersistenceAdapterCancellationTest {

	private static final UUID AUDIT_ID = UUID.randomUUID();

	private static final UUID DOCUMENT_ID = UUID.randomUUID();

	private static final UUID OWNER_ID = UUID.randomUUID();

	private static final UUID CORRELATION_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-10-26T15:00:00Z");

	private static final Instant CANCELLED_AT = Instant.parse("2026-11-27T15:00:00Z");

	private static final String REASON = "Documento registrado por error";

	@Mock
	private EntityManager entityManager;

	@Mock
	private IdGenerator idGenerator;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private AuditLogPersistenceAdapter adapter;

	@BeforeEach
	void setUp() {
		this.adapter = new AuditLogPersistenceAdapter(
				this.entityManager, this.jsonMapper, this.idGenerator);
	}

	@Test
	void should_record_cancellation_with_before_and_after_snapshots() {
		// Arrange
		Document previous = document();

		Document cancelled = previous.cancel(REASON, CANCELLED_AT);

		given(this.idGenerator.newId()).willReturn(AUDIT_ID);

		// Act
		this.adapter.recordCancellation(previous, cancelled, CORRELATION_ID);

		// Assert
		ArgumentCaptor<DocumentAuditLogJpaEntity> captor =
				ArgumentCaptor.forClass(DocumentAuditLogJpaEntity.class);

		verify(this.entityManager).persist(captor.capture());

		DocumentAuditLogJpaEntity entry = captor.getValue();

		assertThat(entry.getId()).isEqualTo(AUDIT_ID);
		assertThat(entry.getDocumentId()).isEqualTo(DOCUMENT_ID);
		assertThat(entry.getAction()).isEqualTo("CANCELLED");
		assertThat(entry.getReason()).isEqualTo(REASON);
		assertThat(entry.getCorrelationId()).isEqualTo(CORRELATION_ID);
		assertThat(entry.getCreatedAt()).isEqualTo(CANCELLED_AT);

		JsonNode changes = this.jsonMapper.readTree(entry.getChanges());

		JsonNode before = changes.get("before");
		JsonNode after = changes.get("after");

		assertThat(before.get("status").asString()).isEqualTo("EXPIRED");
		assertThat(before.get("cancellation_reason").isNull()).isTrue();
		assertThat(before.get("cancelled_at").isNull()).isTrue();
		assertThat(before.get("updated_at").asString()).isEqualTo(CREATED.toString());

		assertThat(after.get("status").asString()).isEqualTo("CANCELLED");
		assertThat(after.get("cancellation_reason").asString()).isEqualTo(REASON);
		assertThat(after.get("cancelled_at").asString()).isEqualTo(CANCELLED_AT.toString());
		assertThat(after.get("updated_at").asString()).isEqualTo(CANCELLED_AT.toString());

		assertUnchangedFields(before, after);

		assertThat(previous.status()).isEqualTo(DocumentStatus.EXPIRED);
		assertThat(cancelled.status()).isEqualTo(DocumentStatus.CANCELLED);

		verify(this.idGenerator).newId();
		verifyNoMoreInteractions(this.entityManager, this.idGenerator);
	}

	private static void assertUnchangedFields(JsonNode before, JsonNode after) {
		assertThat(before.get("document_type").asString()).isEqualTo("SOAT");
		assertThat(before.get("owner_type").asString()).isEqualTo("VEHICLE");
		assertThat(before.get("owner_id").asString()).isEqualTo(OWNER_ID.toString());
		assertThat(before.get("document_number").asString()).isEqualTo("SOAT-12345");
		assertThat(before.get("issuing_entity").asString()).isEqualTo("Aseguradora Ejemplo");
		assertThat(before.get("issue_date").asString()).isEqualTo("2025-10-26");
		assertThat(before.get("expiration_date").asString()).isEqualTo("2026-10-26");
		assertThat(before.get("license_category").isNull()).isTrue();

		assertThat(after.get("document_type")).isEqualTo(before.get("document_type"));
		assertThat(after.get("owner_type")).isEqualTo(before.get("owner_type"));
		assertThat(after.get("owner_id")).isEqualTo(before.get("owner_id"));
		assertThat(after.get("document_number")).isEqualTo(before.get("document_number"));
		assertThat(after.get("issuing_entity")).isEqualTo(before.get("issuing_entity"));
		assertThat(after.get("issue_date")).isEqualTo(before.get("issue_date"));
		assertThat(after.get("expiration_date")).isEqualTo(before.get("expiration_date"));
		assertThat(after.get("license_category")).isEqualTo(before.get("license_category"));
	}

	private static Document document() {
		DocumentJpaEntity entity = new DocumentJpaEntity(
				DOCUMENT_ID,
				"SOAT",
				"VEHICLE",
				OWNER_ID,
				"SOAT-12345",
				"Aseguradora Ejemplo",
				LocalDate.of(2025, 10, 26),
				LocalDate.of(2026, 10, 26),
				"EXPIRED",
				null,
				CREATED,
				CREATED);

		return DocumentPersistenceMapper.toDomain(entity);
	}

}
