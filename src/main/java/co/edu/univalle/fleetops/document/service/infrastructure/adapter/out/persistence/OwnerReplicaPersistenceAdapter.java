package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Component;

import co.edu.univalle.fleetops.document.service.application.port.out.OwnerReplicaRepository;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DriverReplicaJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.VehicleReplicaJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DriverReplicaJpaRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.VehicleReplicaJpaRepository;

/**
 * Adaptador de {@link OwnerReplicaRepository}: lee y escribe {@code vehicle_replica} o {@code driver_replica} según
 * el tipo de propietario (ADR-DOC-06, HU-DOC-10).
 *
 * <p>{@link #lastEventAt(OwnerRef)} lee la fila con {@code SELECT ... FOR UPDATE}: dos eventos de la misma entidad
 * procesados a la vez (por ejemplo, en dos instancias del servicio) se aplican uno detrás del otro. Las escrituras
 * siguientes de la misma transacción reutilizan la entidad ya cargada.
 */
@Component
public class OwnerReplicaPersistenceAdapter implements OwnerReplicaRepository {

	private final VehicleReplicaJpaRepository vehicles;

	private final DriverReplicaJpaRepository drivers;

	private final EntityManager entityManager;

	public OwnerReplicaPersistenceAdapter(VehicleReplicaJpaRepository vehicles, DriverReplicaJpaRepository drivers,
			EntityManager entityManager) {
		this.vehicles = vehicles;
		this.drivers = drivers;
		this.entityManager = entityManager;
	}

	@Override
	public boolean isActiveOwner(OwnerRef owner) {
		return switch (owner.ownerType()) {
			case VEHICLE -> this.vehicles.existsByVehicleIdAndActiveTrue(owner.ownerId());
			case DRIVER -> this.drivers.existsByDriverIdAndActiveTrue(owner.ownerId());
		};
	}

	@Override
	public Optional<Instant> lastEventAt(OwnerRef owner) {
		return switch (owner.ownerType()) {
			case VEHICLE -> lockVehicle(owner).map(VehicleReplicaJpaEntity::getLastEventAt);
			case DRIVER -> lockDriver(owner).map(DriverReplicaJpaEntity::getLastEventAt);
		};
	}

	@Override
	public void activate(OwnerRef owner, String label, Instant eventAt, Instant updatedAt) {
		switch (owner.ownerType()) {
			case VEHICLE -> lockVehicle(owner).ifPresentOrElse(
					vehicle -> vehicle.activate(label, eventAt, updatedAt),
					() -> this.entityManager
						.persist(new VehicleReplicaJpaEntity(owner.ownerId(), label, eventAt, updatedAt)));
			case DRIVER -> lockDriver(owner).ifPresentOrElse(
					driver -> driver.activate(label, eventAt, updatedAt),
					() -> this.entityManager
						.persist(new DriverReplicaJpaEntity(owner.ownerId(), label, eventAt, updatedAt)));
			default -> throw new IllegalArgumentException("Tipo de propietario no soportado: " + owner.ownerType());
		}
	}

	@Override
	public void deactivate(OwnerRef owner, Instant eventAt, Instant updatedAt) {
		switch (owner.ownerType()) {
			case VEHICLE -> lockVehicle(owner).orElseThrow(() -> notFound(owner)).deactivate(eventAt, updatedAt);
			case DRIVER -> lockDriver(owner).orElseThrow(() -> notFound(owner)).deactivate(eventAt, updatedAt);
			default -> throw new IllegalArgumentException("Tipo de propietario no soportado: " + owner.ownerType());
		}
	}

	private Optional<VehicleReplicaJpaEntity> lockVehicle(OwnerRef owner) {
		return Optional.ofNullable(this.entityManager.find(VehicleReplicaJpaEntity.class, owner.ownerId(),
				LockModeType.PESSIMISTIC_WRITE));
	}

	private Optional<DriverReplicaJpaEntity> lockDriver(OwnerRef owner) {
		return Optional.ofNullable(this.entityManager.find(DriverReplicaJpaEntity.class, owner.ownerId(),
				LockModeType.PESSIMISTIC_WRITE));
	}

	private static IllegalStateException notFound(OwnerRef owner) {
		return new IllegalStateException("El propietario no está en la réplica: " + owner);
	}

}
