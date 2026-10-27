package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Tabla {@code document.driver_replica}: réplica mínima de Conductores (ADR-DOC-06). Solo lectura en esta versión:
 * mientras no exista el consumidor de eventos, se carga con {@code database/document_seed_dev.sql}.
 */
@Entity
@Immutable
@Table(name = "driver_replica")
public class DriverReplicaJpaEntity {

	@Id
	@Column(name = "driver_id", nullable = false)
	private UUID driverId;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	protected DriverReplicaJpaEntity() {
	}

	public UUID getDriverId() {
		return this.driverId;
	}

	public String getFullName() {
		return this.fullName;
	}

	public boolean isActive() {
		return this.active;
	}

}
