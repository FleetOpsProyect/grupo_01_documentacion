package co.edu.univalle.fleetops.document.service.application.port.out;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Puerto de salida del reloj (diagrama de arquetipo, «Puertos de salida: reloj»).
 *
 * <p>La «fecha actual» de negocio se calcula en la zona horaria del DAS (America/Bogota) y es la base de la
 * regla única de estado (ADR-DOC-01). Las marcas de tiempo se manejan en UTC (DAS sec. 7).
 */
public interface BusinessClock {

	/**
	 * Fecha actual de negocio, usada para calcular days_remaining y el estado de los documentos.
	 */
	LocalDate today();

	/**
	 * Instante actual en UTC, usado para created_at, updated_at y occurred_at.
	 */
	Instant now();

}
