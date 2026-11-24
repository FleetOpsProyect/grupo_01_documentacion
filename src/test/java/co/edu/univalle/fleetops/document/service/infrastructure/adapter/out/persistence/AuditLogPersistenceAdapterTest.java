package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentAuditLogJpaEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogPersistenceAdapterTest {

	private static final UUID AUDIT_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000b1");

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID RUN_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000a1");

	private static final Instant NOW = Instant.parse("2026-11-23T06:00:00Z");

	@Mock
	private EntityManager entityManager;

	@Mock
	private IdGenerator idGenerator;

	private AuditLogPersistenceAdapter adapter;

	@BeforeEach
	void setUp() {
		this.adapter = new AuditLogPersistenceAdapter(this.entityManager, JsonMapper.builder().build(),
				this.idGenerator);
	}

	@Test
	void should_record_the_status_change_made_by_the_job() {
		// Arrange
		given(this.idGenerator.newId()).willReturn(AUDIT_ID);

		// Act
		this.adapter.recordStatusChange(DOCUMENT_ID, DocumentStatus.VALID, DocumentStatus.EXPIRING_SOON, RUN_ID, NOW);

		// Assert (HU-DOC-06, criterio 6)
		ArgumentCaptor<DocumentAuditLogJpaEntity> entry = ArgumentCaptor.forClass(DocumentAuditLogJpaEntity.class);
		verify(this.entityManager).persist(entry.capture());
		assertThat(entry.getValue()).extracting(DocumentAuditLogJpaEntity::getId,
				DocumentAuditLogJpaEntity::getDocumentId, DocumentAuditLogJpaEntity::getAction,
				DocumentAuditLogJpaEntity::getCorrelationId, DocumentAuditLogJpaEntity::getCreatedAt)
			.containsExactly(AUDIT_ID, DOCUMENT_ID, "STATUS_CHANGED", RUN_ID, NOW);
		assertThat(entry.getValue().getChanges())
			.isEqualTo("{\"before\":{\"status\":\"VALID\"},\"after\":{\"status\":\"EXPIRING_SOON\"}}");
	}

}
