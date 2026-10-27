package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DriverReplicaJpaEntity;

public interface DriverReplicaJpaRepository extends JpaRepository<DriverReplicaJpaEntity, UUID> {

	boolean existsByDriverIdAndActiveTrue(UUID driverId);

}
