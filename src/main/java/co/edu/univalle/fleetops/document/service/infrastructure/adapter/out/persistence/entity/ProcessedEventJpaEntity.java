package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Tabla {@code document.processed_events}: event_id de los eventos ya consumidos (DAS sec. 8.2, HU-DOC-10). Solo
 * se insertan filas.
 */
@Entity
@Immutable
@Table(name = "processed_events")
public class ProcessedEventJpaEntity {

	@Id
	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(name = "event_type", nullable = false, length = 60)
	private String eventType;

	@Column(name = "source", nullable = false, length = 150)
	private String source;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected ProcessedEventJpaEntity() {
	}

	public ProcessedEventJpaEntity(UUID eventId, String eventType, String source, Instant processedAt) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.source = source;
		this.processedAt = processedAt;
	}

	public UUID getEventId() {
		return this.eventId;
	}

	public String getEventType() {
		return this.eventType;
	}

	public String getSource() {
		return this.source;
	}

	public Instant getProcessedAt() {
		return this.processedAt;
	}

}
