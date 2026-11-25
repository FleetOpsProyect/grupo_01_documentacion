package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository.PendingEvent;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpired;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.OutboxEventJpaRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.config.DocumentServiceProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPersistenceAdapterTest {

	private static final UUID EVENT_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000e1");

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID CORRELATION_ID = UUID.fromString("6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");

	private static final Instant CREATED = Instant.parse("2026-11-24T15:00:00Z");

	@Mock
	private EntityManager entityManager;

	@Mock
	private OutboxEventJpaRepository repository;

	@Mock
	private ApplicationEventPublisher applicationEvents;

	private OutboxPersistenceAdapter adapter;

	@BeforeEach
	void setUp() {
		this.adapter = new OutboxPersistenceAdapter(this.entityManager, this.repository, JsonMapper.builder().build(),
				new DocumentServiceProperties(ZoneId.of("America/Bogota"), List.of(30, 15, 7, 1)),
				this.applicationEvents);
	}

	private static OutboxEventJpaEntity pendingEntity() {
		return new OutboxEventJpaEntity(EVENT_ID, "document_expired_event", "fleetops_document_events",
				"01960000-0000-7000-8000-000000000001", DOCUMENT_ID, "{\"event_id\": \"" + EVENT_ID + "\"}",
				CORRELATION_ID, CREATED);
	}

	@Test
	void should_store_the_event_and_announce_it_for_after_the_commit() {
		// Arrange
		DocumentExpired event = new DocumentExpired(DOCUMENT_ID, DocumentTypeCode.SOAT,
				new OwnerRef(OwnerType.VEHICLE, UUID.fromString("01960000-0000-7000-8000-000000000001")),
				LocalDate.of(2026, 11, 20), CREATED);

		// Act
		this.adapter.append(EVENT_ID, event, CORRELATION_ID);

		// Assert
		ArgumentCaptor<OutboxEventJpaEntity> stored = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
		verify(this.entityManager).persist(stored.capture());
		assertThat(stored.getValue().getStatus()).isEqualTo(OutboxEventJpaEntity.STATUS_PENDING);
		assertThat(stored.getValue().getPayload()).contains("\"occurred_at\":\"2026-11-24T10:00:00-05:00\"");
		verify(this.applicationEvents).publishEvent(any(OutboxEventsAppended.class));
	}

	@Test
	void should_lock_pending_events_as_ready_to_publish() {
		given(this.repository.lockPending(50)).willReturn(List.of(pendingEntity()));

		assertThat(this.adapter.lockPending(50)).containsExactly(new PendingEvent(EVENT_ID, "document_expired_event",
				"fleetops_document_events", "{\"event_id\": \"" + EVENT_ID + "\"}", CORRELATION_ID));
	}

	@Test
	void should_mark_an_event_published() {
		// Arrange
		OutboxEventJpaEntity entity = pendingEntity();
		entity.recordFailure("SNS no está disponible", false);
		given(this.entityManager.find(OutboxEventJpaEntity.class, EVENT_ID)).willReturn(entity);
		Instant now = CREATED.plusSeconds(3);

		// Act
		this.adapter.markPublished(EVENT_ID, now);

		// Assert
		assertThat(entity).extracting(OutboxEventJpaEntity::getStatus, OutboxEventJpaEntity::getAttempts,
				OutboxEventJpaEntity::getLastError, OutboxEventJpaEntity::getPublishedAt)
			.containsExactly("PUBLISHED", 2, null, now);
	}

	@Test
	void should_keep_a_transiently_failed_event_pending() {
		OutboxEventJpaEntity entity = pendingEntity();
		given(this.entityManager.find(OutboxEventJpaEntity.class, EVENT_ID)).willReturn(entity);

		this.adapter.recordFailure(EVENT_ID, "SNS no está disponible", false);

		assertThat(entity).extracting(OutboxEventJpaEntity::getStatus, OutboxEventJpaEntity::getAttempts,
				OutboxEventJpaEntity::getLastError)
			.containsExactly("PENDING", 1, "SNS no está disponible");
	}

	@Test
	void should_mark_failed_a_rejected_event_and_truncate_the_error() {
		OutboxEventJpaEntity entity = pendingEntity();
		given(this.entityManager.find(OutboxEventJpaEntity.class, EVENT_ID)).willReturn(entity);

		this.adapter.recordFailure(EVENT_ID, "e".repeat(1500), true);

		assertThat(entity.getStatus()).isEqualTo("FAILED");
		assertThat(entity.getLastError()).hasSize(OutboxEventJpaEntity.MAX_ERROR_LENGTH);
		assertThat(entity.getPublishedAt()).isNull();
	}

	@Test
	void should_accept_a_failure_without_message() {
		OutboxEventJpaEntity entity = pendingEntity();
		entity.recordFailure(null, false);

		assertThat(entity.getLastError()).isNull();
		assertThat(entity.getAttempts()).isEqualTo(1);
	}

	@Test
	void should_fail_on_an_event_missing_from_the_outbox() {
		assertThatIllegalStateException().isThrownBy(() -> this.adapter.markPublished(EVENT_ID, CREATED))
			.withMessageContaining(EVENT_ID.toString());
	}

	@Test
	void should_expose_the_outbox_lag_as_metrics() {
		// Arrange
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		Clock clock = Clock.fixed(CREATED.plus(Duration.ofSeconds(90)), ZoneOffset.UTC);
		given(this.repository.countByStatus("PENDING")).willReturn(4L);
		given(this.repository.countByStatus("FAILED")).willReturn(1L);
		given(this.repository.findOldestPendingCreatedAt()).willReturn(CREATED, (Instant) null);
		new OutboxMetrics(this.repository, clock, registry);

		// Act + Assert
		assertThat(registry.get(OutboxMetrics.EVENTS).tag("status", "pending").gauge().value()).isEqualTo(4);
		assertThat(registry.get(OutboxMetrics.EVENTS).tag("status", "failed").gauge().value()).isEqualTo(1);
		assertThat(registry.get(OutboxMetrics.OLDEST_PENDING_AGE).gauge().value()).isEqualTo(90);
		assertThat(registry.get(OutboxMetrics.OLDEST_PENDING_AGE).gauge().value()).isZero();
	}

}
