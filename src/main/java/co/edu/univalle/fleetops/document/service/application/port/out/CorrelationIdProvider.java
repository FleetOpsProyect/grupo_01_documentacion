package co.edu.univalle.fleetops.document.service.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: correlation_id de la operación en curso (DAS sec. 10). El núcleo lo obtiene sin conocer el
 * mecanismo (MDC, cabecera HTTP o mensaje).
 */
public interface CorrelationIdProvider {

	Optional<UUID> currentCorrelationId();

}
