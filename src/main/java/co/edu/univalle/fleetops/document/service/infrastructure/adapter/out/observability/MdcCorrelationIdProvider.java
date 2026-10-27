package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.observability;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;

import co.edu.univalle.fleetops.document.service.application.port.out.CorrelationIdProvider;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest.CorrelationIdFilter;

/**
 * Lee el correlation_id que {@link CorrelationIdFilter} deja en el MDC de la petición (DAS sec. 10).
 */
public final class MdcCorrelationIdProvider implements CorrelationIdProvider {

	@Override
	public Optional<UUID> currentCorrelationId() {
		String value = MDC.get(CorrelationIdFilter.MDC_KEY);
		if (value == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(value));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
