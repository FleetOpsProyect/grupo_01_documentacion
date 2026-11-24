package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DriverReplicaJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.VehicleReplicaJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DriverReplicaJpaRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.VehicleReplicaJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OwnerReplicaPersistenceAdapterTest {

	private static final OwnerRef VEHICLE = new OwnerRef(OwnerType.VEHICLE,
			UUID.fromString("01960000-0000-7000-8000-000000000001"));

	private static final OwnerRef DRIVER = new OwnerRef(OwnerType.DRIVER,
			UUID.fromString("01960000-0000-7000-8000-000000000101"));

	@Mock
	private VehicleReplicaJpaRepository vehicles;

	@Mock
	private DriverReplicaJpaRepository drivers;

	@Mock
	private EntityManager entityManager;

	@InjectMocks
	private OwnerReplicaPersistenceAdapter adapter;

	@Test
	void should_find_an_existing_vehicle_replica() {
		given(this.entityManager.find(VehicleReplicaJpaEntity.class, VEHICLE.ownerId()))
			.willReturn(new VehicleReplicaJpaEntity(VEHICLE.ownerId(), "ABC123",
					java.time.Instant.parse("2026-10-26T15:04:05Z"),
					java.time.Instant.parse("2026-10-26T15:04:05Z")));

		assertThat(this.adapter.existsOwner(VEHICLE)).isTrue();
		verify(this.entityManager).find(VehicleReplicaJpaEntity.class, VEHICLE.ownerId());
	}

	@Test
	void should_find_an_existing_driver_replica() {
		given(this.entityManager.find(DriverReplicaJpaEntity.class, DRIVER.ownerId()))
			.willReturn(new DriverReplicaJpaEntity(DRIVER.ownerId(), "Conductor Demo",
					java.time.Instant.parse("2026-10-26T15:04:05Z"),
					java.time.Instant.parse("2026-10-26T15:04:05Z")));

		assertThat(this.adapter.existsOwner(DRIVER)).isTrue();
		verify(this.entityManager).find(DriverReplicaJpaEntity.class, DRIVER.ownerId());
	}

	@Test
	void should_return_false_when_the_owner_is_not_in_its_replica() {
		assertThat(this.adapter.existsOwner(VEHICLE)).isFalse();
		verify(this.entityManager).find(VehicleReplicaJpaEntity.class, VEHICLE.ownerId());
	}

}
