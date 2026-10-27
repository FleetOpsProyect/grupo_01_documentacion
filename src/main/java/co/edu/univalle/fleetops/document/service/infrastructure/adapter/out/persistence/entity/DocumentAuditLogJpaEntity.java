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
 * Tabla {@code document.document_audit_log}: auditoría interna de cada cambio (DAS sec. 6.4). Guarda el
 * document_number completo; nunca se expone en logs ni en eventos.
 */
@Entity
@Table(name = "document_audit_log")
public class DocumentAuditLogJpaEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Column(name = "action", nullable = false, length = 30)
	private String action;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "changes", nullable = false)
	private String changes;

	@Column(name = "reason", length = 200)
	private String reason;

	@Column(name = "correlation_id")
	private UUID correlationId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentAuditLogJpaEntity() {
	}

	public DocumentAuditLogJpaEntity(UUID id, UUID documentId, String action, String changes, String reason,
			UUID correlationId, Instant createdAt) {
		this.id = id;
		this.documentId = documentId;
		this.action = action;
		this.changes = changes;
		this.reason = reason;
		this.correlationId = correlationId;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getDocumentId() {
		return this.documentId;
	}

	public String getAction() {
		return this.action;
	}

	public String getChanges() {
		return this.changes;
	}

	public String getReason() {
		return this.reason;
	}

	public UUID getCorrelationId() {
		return this.correlationId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
