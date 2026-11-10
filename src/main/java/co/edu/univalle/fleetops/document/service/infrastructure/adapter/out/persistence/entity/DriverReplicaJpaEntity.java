package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla {@code document.driver_replica}: réplica mínima de Conductores (ADR-DOC-06). La alimenta el consumidor de
 * eventos de Conductores (HU-DOC-10); en desarrollo también la carga {@code database/document_seed_dev.sql}.
 */
@Entity
@Table(name = "driver_replica")
public class DriverReplicaJpaEntity {

	@Id
	@Column(name = "driver_id", nullable = false)
	private UUID driverId;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "last_event_at", nullable = false)
	private Instant lastEventAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DriverReplicaJpaEntity() {
	}

	public DriverReplicaJpaEntity(UUID driverId, String fullName, Instant lastEventAt, Instant updatedAt) {
		this.driverId = driverId;
		activate(fullName, lastEventAt, updatedAt);
	}

	public final void activate(String newFullName, Instant eventAt, Instant changedAt) {
		this.fullName = newFullName;
		this.active = true;
		this.lastEventAt = eventAt;
		this.updatedAt = changedAt;
	}

	public void deactivate(Instant eventAt, Instant changedAt) {
		this.active = false;
		this.lastEventAt = eventAt;
		this.updatedAt = changedAt;
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

	public Instant getLastEventAt() {
		return this.lastEventAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
