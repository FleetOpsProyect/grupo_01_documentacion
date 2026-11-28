package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

/**
 * Cuerpo de la petición REST para anular un documento (HU-DOC-04).
 *
 * @param reason motivo de anulación
 */
public record CancelDocumentRequest(String reason) {

}
