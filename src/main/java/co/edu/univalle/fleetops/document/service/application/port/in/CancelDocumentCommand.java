package co.edu.univalle.fleetops.document.service.application.port.in;

import java.util.UUID;

/**
 * Command: datos para anular un documento (HU-DOC-04).
 *
 * @param documentId identificador del documento
 * @param reason motivo de anulación
 */
public record CancelDocumentCommand(UUID documentId, String reason) {

}
