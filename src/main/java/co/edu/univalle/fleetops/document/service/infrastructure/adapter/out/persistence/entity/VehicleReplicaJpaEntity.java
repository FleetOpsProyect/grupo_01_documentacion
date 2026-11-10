package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla {@code document.vehicle_replica}: réplica mínima de Vehículos (ADR-DOC-06). La alimenta el consumidor de
 * eventos de Vehículos (HU-DOC-10); en desarrollo también la carga {@code database/document_seed_dev.sql}.
 */
@Entity
@Table(name = "vehicle_replica")
public class VehicleReplicaJpaEntity {

	@Id
	@Column(name = "vehicle_id", nullable = false)
	private UUID vehicleId;

	@Column(name = "license_plate", nullable = false, length = 10)
	private String licensePlate;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "last_event_at", nullable = false)
	private Instant lastEventAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected VehicleReplicaJpaEntity() {
	}

	public VehicleReplicaJpaEntity(UUID vehicleId, String licensePlate, Instant lastEventAt, Instant updatedAt) {
		this.vehicleId = vehicleId;
		activate(licensePlate, lastEventAt, updatedAt);
	}

	public final void activate(String newLicensePlate, Instant eventAt, Instant changedAt) {
		this.licensePlate = newLicensePlate;
		this.active = true;
		this.lastEventAt = eventAt;
		this.updatedAt = changedAt;
	}

	public void deactivate(Instant eventAt, Instant changedAt) {
		this.active = false;
		this.lastEventAt = eventAt;
		this.updatedAt = changedAt;
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

	public Instant getLastEventAt() {
		return this.lastEventAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
