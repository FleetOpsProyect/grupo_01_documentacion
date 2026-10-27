package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.id;

import java.time.Clock;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;

/**
 * Genera UUID versión 7 (RFC 9562): 48 bits de marca de tiempo en milisegundos y el resto aleatorio. Son ordenables
 * en el tiempo, igual que los que crea {@code uuidv7()} en PostgreSQL 18, y no fragmentan los índices.
 */
public final class UuidV7Generator implements IdGenerator {

	private static final long TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL;

	private static final long VERSION_7 = 0x7000L;

	private static final long VARIANT_MASK = 0x3FFF_FFFF_FFFF_FFFFL;

	private static final long VARIANT_RFC_9562 = 0x8000_0000_0000_0000L;

	private final Clock clock;

	private final Random random;

	/**
	 * @param clock reloj del que se toma la marca de tiempo
	 * @param random fuente aleatoria; en producción, {@link java.security.SecureRandom}
	 */
	public UuidV7Generator(Clock clock, Random random) {
		this.clock = Objects.requireNonNull(clock, "clock");
		this.random = Objects.requireNonNull(random, "random");
	}

	@Override
	public UUID newId() {
		long timestamp = this.clock.millis() & TIMESTAMP_MASK;
		long randA = this.random.nextInt(1 << 12);
		long mostSignificant = (timestamp << 16) | VERSION_7 | randA;
		long leastSignificant = (this.random.nextLong() & VARIANT_MASK) | VARIANT_RFC_9562;
		return new UUID(mostSignificant, leastSignificant);
	}

}
