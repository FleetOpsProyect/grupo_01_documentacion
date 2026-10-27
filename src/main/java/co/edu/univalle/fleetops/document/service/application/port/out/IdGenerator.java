package co.edu.univalle.fleetops.document.service.application.port.out;

import java.util.UUID;

/**
 * Puerto de salida: generación de identificadores. El núcleo asigna los ids para conocerlos antes de persistir
 * (cabecera Location y aggregate_id del outbox).
 */
public interface IdGenerator {

	UUID newId();

}
