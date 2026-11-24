package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Tabla {@code document.issued_alerts}: umbrales ya emitidos por documento (HU-DOC-06, ADR-DOC-02 y ADR-DOC-03).
 * Solo se insertan filas.
 */
@Entity
@Immutable
@Table(name = "issued_alerts")
public class IssuedAlertJpaEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "document_id", nullable = false)
	private UUID documentId;

	@Column(name = "threshold_days", nullable = false)
	private int thresholdDays;

	@Column(name = "published", nullable = false)
	private boolean published;

	@Column(name = "outbox_event_id")
	private UUID outboxEventId;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	protected IssuedAlertJpaEntity() {
	}

	/**
	 * @param outboxEventId event_id publicado; {@code null} marca el umbral como emitido sin evento
	 */
	public IssuedAlertJpaEntity(UUID id, UUID documentId, int thresholdDays, UUID outboxEventId, Instant issuedAt) {
		this.id = id;
		this.documentId = documentId;
		this.thresholdDays = thresholdDays;
		this.published = outboxEventId != null;
		this.outboxEventId = outboxEventId;
		this.issuedAt = issuedAt;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getDocumentId() {
		return this.documentId;
	}

	public int getThresholdDays() {
		return this.thresholdDays;
	}

	public boolean isPublished() {
		return this.published;
	}

	public UUID getOutboxEventId() {
		return this.outboxEventId;
	}

	public Instant getIssuedAt() {
		return this.issuedAt;
	}

}
