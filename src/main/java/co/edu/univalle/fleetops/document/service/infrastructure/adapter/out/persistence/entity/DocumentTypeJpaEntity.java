package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Tabla {@code document.document_types}: catálogo de los tipos y sus reglas (ADR-DOC-10, DAS sec. 14.4). Solo
 * lectura: el catálogo se carga con {@code database/document_schema.sql}.
 */
@Entity
@Immutable
@Table(name = "document_types")
public class DocumentTypeJpaEntity {

	@Id
	@Column(name = "code", nullable = false, length = 30)
	private String code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "owner_type", nullable = false, length = 10)
	private String ownerType;

	@Column(name = "has_expiration", nullable = false)
	private boolean hasExpiration;

	@Column(name = "is_mandatory", nullable = false)
	private boolean mandatory;

	@Column(name = "is_renewable", nullable = false)
	private boolean renewable;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	protected DocumentTypeJpaEntity() {
	}

	public DocumentTypeJpaEntity(String code, String name, String ownerType, boolean hasExpiration,
			boolean mandatory, boolean renewable, boolean active) {
		this.code = code;
		this.name = name;
		this.ownerType = ownerType;
		this.hasExpiration = hasExpiration;
		this.mandatory = mandatory;
		this.renewable = renewable;
		this.active = active;
	}

	public String getCode() {
		return this.code;
	}

	public String getName() {
		return this.name;
	}

	public String getOwnerType() {
		return this.ownerType;
	}

	public boolean hasExpiration() {
		return this.hasExpiration;
	}

	public boolean isMandatory() {
		return this.mandatory;
	}

	public boolean isRenewable() {
		return this.renewable;
	}

	public boolean isActive() {
		return this.active;
	}

}
