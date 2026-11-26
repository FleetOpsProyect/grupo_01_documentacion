package co.edu.univalle.fleetops.document.service.application.port.in;

/**
 * Consulta de documentos próximos a vencer (HU-DOC-08). Solo lee: no modifica datos ni publica eventos.
 */
public interface ExpiringSoonUseCase {

	ExpiringSoonView findExpiringSoon(ExpiringSoonQuery query);

}
