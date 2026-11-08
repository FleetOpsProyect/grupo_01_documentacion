package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TimestampFormatTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	@Test
	void should_show_the_instant_with_the_bogota_offset() {
		// Act
		String text = TimestampFormat.format(Instant.parse("2026-10-26T15:04:05Z"), BOGOTA);

		// Assert (DAS sec. 7, decisión P-02 del 08-11-2026)
		assertThat(text).isEqualTo("2026-10-26T10:04:05-05:00");
	}

	@Test
	void should_keep_the_seconds_when_they_are_zero() {
		// Act
		String text = TimestampFormat.format(Instant.parse("2026-10-27T05:00:00Z"), BOGOTA);

		// Assert: OffsetDateTime.toString() los omitiría ("2026-10-27T00:00-05:00")
		assertThat(text).isEqualTo("2026-10-27T00:00:00-05:00");
	}

	@Test
	void should_keep_the_fraction_of_second() {
		// Act
		String text = TimestampFormat.format(Instant.parse("2026-10-27T16:39:01.167567509Z"), BOGOTA);

		// Assert
		assertThat(text).isEqualTo("2026-10-27T11:39:01.167567509-05:00");
	}

	@Test
	void should_reject_missing_arguments() {
		assertThatNullPointerException().isThrownBy(() -> TimestampFormat.format(null, BOGOTA)).withMessage("instant");
		assertThatNullPointerException().isThrownBy(() -> TimestampFormat.format(Instant.EPOCH, null))
			.withMessage("zone");
	}

}
