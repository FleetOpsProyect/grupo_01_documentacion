package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.id;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

	private static final Instant NOW = Instant.parse("2026-10-26T15:04:05.123Z");

	@Test
	void should_generate_a_version_7_rfc_9562_uuid_with_the_clock_timestamp() {
		// Arrange
		UuidV7Generator generator = new UuidV7Generator(Clock.fixed(NOW, ZoneOffset.UTC), new Random(42));

		// Act
		UUID id = generator.newId();

		// Assert
		assertThat(id.version()).isEqualTo(7);
		assertThat(id.variant()).isEqualTo(2);
		assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
	}

	@Test
	void should_order_ids_by_creation_time() {
		// Arrange
		UuidV7Generator earlier = new UuidV7Generator(Clock.fixed(NOW, ZoneOffset.UTC), new Random(1));
		UuidV7Generator later = new UuidV7Generator(Clock.fixed(NOW.plusMillis(1), ZoneOffset.UTC), new Random(2));

		// Act
		String first = earlier.newId().toString();
		String second = later.newId().toString();

		// Assert
		assertThat(first).isLessThan(second);
	}

	@Test
	void should_generate_different_ids_in_the_same_millisecond() {
		// Arrange
		UuidV7Generator generator = new UuidV7Generator(Clock.fixed(NOW, ZoneOffset.UTC), new Random(7));

		// Act
		UUID first = generator.newId();
		UUID second = generator.newId();

		// Assert
		assertThat(first).isNotEqualTo(second);
	}

}
