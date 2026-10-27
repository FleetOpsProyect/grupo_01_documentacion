package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Tabla {@code document.outbox_events} (ADR-DOC-05). Se inserta en la misma transacción que el documento; en esta
 * versión no hay publicador y las filas quedan en PENDING.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEventJpaEntity {

	public static final String STATUS_PENDING = "PENDING";

	@Id
	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

	@Column(name = "event_type", nullable = false, length = 60)
	private String eventType;

	@Column(name = "topic", nullable = false, length = 150)
	private String topic;

	@Column(name = "message_key", nullable = false, length = 36)
	private String messageKey;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false)
	private String payload;

	@Column(name = "correlation_id")
	private UUID correlationId;

	@Column(name = "status", nullable = false, length = 15)
	private String status;

	@Column(name = "attempts", nullable = false)
	private int attempts;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxEventJpaEntity() {
	}

	public OutboxEventJpaEntity(UUID eventId, String eventType, String topic, String messageKey, UUID aggregateId,
			String payload, UUID correlationId, Instant createdAt) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.topic = topic;
		this.messageKey = messageKey;
		this.aggregateId = aggregateId;
		this.payload = payload;
		this.correlationId = correlationId;
		this.status = STATUS_PENDING;
		this.attempts = 0;
		this.createdAt = createdAt;
	}

	public UUID getEventId() {
		return this.eventId;
	}

	public String getEventType() {
		return this.eventType;
	}

	public String getTopic() {
		return this.topic;
	}

	public String getMessageKey() {
		return this.messageKey;
	}

	public UUID getAggregateId() {
		return this.aggregateId;
	}

	public String getPayload() {
		return this.payload;
	}

	public UUID getCorrelationId() {
		return this.correlationId;
	}

	public String getStatus() {
		return this.status;
	}

	public int getAttempts() {
		return this.attempts;
	}

	public String getLastError() {
		return this.lastError;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getPublishedAt() {
		return this.publishedAt;
	}

}
