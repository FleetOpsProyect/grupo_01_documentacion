package co.edu.univalle.fleetops.document.service.domain.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OwnerRefTest {

	private static final UUID VEHICLE_ID = UUID.fromString("01960000-0000-7000-8000-000000000001");

	@Test
	void should_keep_owner_type_and_id_when_created() {
		// Act
		OwnerRef owner = new OwnerRef(OwnerType.VEHICLE, VEHICLE_ID);

		// Assert
		assertThat(owner.ownerType()).isEqualTo(OwnerType.VEHICLE);
		assertThat(owner.ownerId()).isEqualTo(VEHICLE_ID);
	}

	@Test
	void should_reject_missing_owner_type() {
		assertThatNullPointerException().isThrownBy(() -> new OwnerRef(null, VEHICLE_ID)).withMessage("ownerType");
	}

	@Test
	void should_reject_missing_owner_id() {
		assertThatNullPointerException().isThrownBy(() -> new OwnerRef(OwnerType.DRIVER, null))
			.withMessage("ownerId");
	}

}
