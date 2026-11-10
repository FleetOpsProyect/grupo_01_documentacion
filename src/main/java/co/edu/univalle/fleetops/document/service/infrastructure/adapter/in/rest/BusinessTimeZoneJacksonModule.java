package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import co.edu.univalle.fleetops.document.service.infrastructure.config.TimestampFormat;

/**
 * Hace que toda marca de tiempo ({@link Instant}) de la API salga en ISO 8601 con el desfase de la zona de negocio,
 * por ejemplo {@code 2026-10-26T10:04:05-05:00} (decisión del equipo del 08-11-2026, P-02). Sin este módulo,
 * Jackson escribe los {@link Instant} en UTC ({@code ...Z}). Spring Boot registra los módulos de Jackson que son
 * beans, así que basta con que esta clase sea un componente.
 */
@Component
public final class BusinessTimeZoneJacksonModule extends SimpleModule {

	private static final long serialVersionUID = 1L;

	public BusinessTimeZoneJacksonModule(@Value("${fleetops.documents.business-time-zone}") ZoneId businessZone) {
		super("business-time-zone");
		addSerializer(new BusinessZoneInstantSerializer(businessZone));
	}

	static final class BusinessZoneInstantSerializer extends StdSerializer<Instant> {

		private final transient ZoneId zone;

		BusinessZoneInstantSerializer(ZoneId zone) {
			super(Instant.class);
			this.zone = zone;
		}

		@Override
		public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
			generator.writeString(TimestampFormat.format(value, this.zone));
		}

	}

}
