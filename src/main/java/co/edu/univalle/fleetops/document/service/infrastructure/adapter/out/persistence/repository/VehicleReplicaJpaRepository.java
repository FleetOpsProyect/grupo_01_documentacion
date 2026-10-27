package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.VehicleReplicaJpaEntity;

public interface VehicleReplicaJpaRepository extends JpaRepository<VehicleReplicaJpaEntity, UUID> {

	boolean existsByVehicleIdAndActiveTrue(UUID vehicleId);

}
