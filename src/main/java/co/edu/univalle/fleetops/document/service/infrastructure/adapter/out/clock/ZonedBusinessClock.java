package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;

/**
 * Adaptador del reloj de negocio: la fecha se calcula en la zona horaria configurada (America/Bogota según el
 * DAS sec. 5) a partir de un {@link Clock} inyectable, para poder fijar la fecha en las pruebas.
 */
public class ZonedBusinessClock implements BusinessClock {

	private final Clock clock;

	private final ZoneId businessZone;

	public ZonedBusinessClock(Clock clock, ZoneId businessZone) {
		this.clock = Objects.requireNonNull(clock, "clock");
		this.businessZone = Objects.requireNonNull(businessZone, "businessZone");
	}

	@Override
	public LocalDate today() {
		return LocalDate.now(this.clock.withZone(this.businessZone));
	}

	@Override
	public Instant now() {
		return this.clock.instant();
	}

}
