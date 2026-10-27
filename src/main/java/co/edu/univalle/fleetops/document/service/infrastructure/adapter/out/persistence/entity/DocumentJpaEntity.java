package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Tabla {@code document.documents} (DAS sec. 14). Separada del agregado {@code Document} para que el dominio no
 * dependa de JPA.
 */
@Entity
@Table(name = "documents")
public class DocumentJpaEntity {

	@Id
	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Column(name = "document_type", nullable = false, updatable = false, length = 30)
	private String documentType;

	@Column(name = "owner_type", nullable = false, updatable = false, length = 10)
	private String ownerType;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(name = "document_number", nullable = false, length = 50)
	private String documentNumber;

	@Column(name = "issuing_entity", nullable = false, length = 150)
	private String issuingEntity;

	@Column(name = "issue_date", nullable = false)
	private LocalDate issueDate;

	@Column(name = "expiration_date")
	private LocalDate expirationDate;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "license_category", length = 5)
	private String licenseCategory;

	@Column(name = "previous_document_id", updatable = false)
	private UUID previousDocumentId;

	@Column(name = "cancellation_reason", length = 200)
	private String cancellationReason;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	/**
	 * Control de concurrencia optimista (ADR-DOC-04). Vacío en un documento nuevo: así Spring Data lo inserta.
	 */
	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DocumentJpaEntity() {
	}

	public DocumentJpaEntity(UUID documentId, String documentType, String ownerType, UUID ownerId,
			String documentNumber, String issuingEntity, LocalDate issueDate, LocalDate expirationDate, String status,
			String licenseCategory, Instant createdAt, Instant updatedAt) {
		this.documentId = documentId;
		this.documentType = documentType;
		this.ownerType = ownerType;
		this.ownerId = ownerId;
		this.documentNumber = documentNumber;
		this.issuingEntity = issuingEntity;
		this.issueDate = issueDate;
		this.expirationDate = expirationDate;
		this.status = status;
		this.licenseCategory = licenseCategory;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public UUID getDocumentId() {
		return this.documentId;
	}

	public String getDocumentType() {
		return this.documentType;
	}

	public String getOwnerType() {
		return this.ownerType;
	}

	public UUID getOwnerId() {
		return this.ownerId;
	}

	public String getDocumentNumber() {
		return this.documentNumber;
	}

	public String getIssuingEntity() {
		return this.issuingEntity;
	}

	public LocalDate getIssueDate() {
		return this.issueDate;
	}

	public LocalDate getExpirationDate() {
		return this.expirationDate;
	}

	public String getStatus() {
		return this.status;
	}

	public String getLicenseCategory() {
		return this.licenseCategory;
	}

	public UUID getPreviousDocumentId() {
		return this.previousDocumentId;
	}

	public String getCancellationReason() {
		return this.cancellationReason;
	}

	public Instant getCancelledAt() {
		return this.cancelledAt;
	}

	public Integer getVersion() {
		return this.version;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
