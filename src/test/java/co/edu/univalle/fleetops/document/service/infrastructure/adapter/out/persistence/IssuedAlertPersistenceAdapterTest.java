package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.IssuedAlertJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.IssuedAlertJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IssuedAlertPersistenceAdapterTest {

	private static final UUID ALERT_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000c1");

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID EVENT_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000e1");

	private static final Instant NOW = Instant.parse("2026-11-23T06:00:00Z");

	@Mock
	private IssuedAlertJpaRepository repository;

	@Mock
	private EntityManager entityManager;

	@Mock
	private IdGenerator idGenerator;

	@InjectMocks
	private IssuedAlertPersistenceAdapter adapter;

	@Test
	void should_return_the_thresholds_already_issued() {
		given(this.repository.findThresholdDaysByDocumentId(DOCUMENT_ID)).willReturn(List.of(30, 15));

		assertThat(this.adapter.issuedThresholds(DOCUMENT_ID)).containsExactlyInAnyOrder(30, 15);
	}

	@Test
	void should_record_a_published_threshold_with_its_event() {
		// Arrange
		given(this.idGenerator.newId()).willReturn(ALERT_ID);

		// Act
		this.adapter.record(DOCUMENT_ID, 30, EVENT_ID, NOW);

		// Assert
		IssuedAlertJpaEntity alert = persisted();
		assertThat(alert).extracting(IssuedAlertJpaEntity::getId, IssuedAlertJpaEntity::getDocumentId,
				IssuedAlertJpaEntity::getThresholdDays, IssuedAlertJpaEntity::isPublished,
				IssuedAlertJpaEntity::getOutboxEventId, IssuedAlertJpaEntity::getIssuedAt)
			.containsExactly(ALERT_ID, DOCUMENT_ID, 30, true, EVENT_ID, NOW);
	}

	@Test
	void should_record_a_silent_threshold_without_event() {
		// Arrange (ADR-DOC-03: published = false si no hay outbox_event_id)
		given(this.idGenerator.newId()).willReturn(ALERT_ID);

		// Act
		this.adapter.record(DOCUMENT_ID, 15, null, NOW);

		// Assert
		IssuedAlertJpaEntity alert = persisted();
		assertThat(alert.isPublished()).isFalse();
		assertThat(alert.getOutboxEventId()).isNull();
	}

	private IssuedAlertJpaEntity persisted() {
		ArgumentCaptor<IssuedAlertJpaEntity> captor = ArgumentCaptor.forClass(IssuedAlertJpaEntity.class);
		verify(this.entityManager).persist(captor.capture());
		return captor.getValue();
	}

}
