package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import org.springframework.stereotype.Component;

import co.edu.univalle.fleetops.document.service.application.port.out.OwnerReplicaRepository;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DriverReplicaJpaRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.VehicleReplicaJpaRepository;

/**
 * Adaptador de {@link OwnerReplicaRepository}: consulta {@code vehicle_replica} o {@code driver_replica} según el
 * tipo de propietario (ADR-DOC-06).
 */
@Component
public class OwnerReplicaPersistenceAdapter implements OwnerReplicaRepository {

	private final VehicleReplicaJpaRepository vehicles;

	private final DriverReplicaJpaRepository drivers;

	public OwnerReplicaPersistenceAdapter(VehicleReplicaJpaRepository vehicles, DriverReplicaJpaRepository drivers) {
		this.vehicles = vehicles;
		this.drivers = drivers;
	}

	@Override
	public boolean isActiveOwner(OwnerRef owner) {
		return switch (owner.ownerType()) {
			case VEHICLE -> this.vehicles.existsByVehicleIdAndActiveTrue(owner.ownerId());
			case DRIVER -> this.drivers.existsByDriverIdAndActiveTrue(owner.ownerId());
		};
	}

}
