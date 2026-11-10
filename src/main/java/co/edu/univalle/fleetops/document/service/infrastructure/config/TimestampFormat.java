package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Formato único de las marcas de tiempo que salen del servicio, en la API y en los eventos: ISO 8601 con el desfase
 * de la zona de negocio, por ejemplo {@code 2026-10-26T10:04:05-05:00} para America/Bogota (decisión del equipo del
 * 08-11-2026, P-02). Los instantes se guardan sin zona ({@code timestamptz}); la zona solo se aplica al mostrarlos.
 */
public final class TimestampFormat {

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private TimestampFormat() {
	}

	/**
	 * @param instant instante a mostrar
	 * @param zone zona de negocio (BUSINESS_TIME_ZONE)
	 * @return el instante en ISO 8601 con el desfase de la zona; siempre incluye los segundos
	 */
	public static String format(Instant instant, ZoneId zone) {
		Objects.requireNonNull(instant, "instant");
		Objects.requireNonNull(zone, "zone");
		return FORMAT.format(instant.atZone(zone));
	}

}
