package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Tabla {@code document.vehicle_replica}: réplica mínima de Vehículos (ADR-DOC-06). Solo lectura en esta versión:
 * mientras no exista el consumidor de eventos, se carga con {@code database/document_seed_dev.sql}.
 */
@Entity
@Immutable
@Table(name = "vehicle_replica")
public class VehicleReplicaJpaEntity {

	@Id
	@Column(name = "vehicle_id", nullable = false)
	private UUID vehicleId;

	@Column(name = "license_plate", nullable = false, length = 10)
	private String licensePlate;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	protected VehicleReplicaJpaEntity() {
	}

	public UUID getVehicleId() {
		return this.vehicleId;
	}

	public String getLicensePlate() {
		return this.licensePlate;
	}

	public boolean isActive() {
		return this.active;
	}

}
