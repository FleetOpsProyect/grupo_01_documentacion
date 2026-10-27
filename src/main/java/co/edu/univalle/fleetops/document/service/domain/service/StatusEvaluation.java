package co.edu.univalle.fleetops.document.service.domain.service;

import java.util.Objects;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;

/**
 * Resultado de la regla única de estado (ADR-DOC-01).
 *
 * @param status VALID, EXPIRING_SOON o EXPIRED
 * @param daysRemaining días hasta el vencimiento según la fecha de negocio; vacío si el documento no vence
 */
public record StatusEvaluation(DocumentStatus status, Integer daysRemaining) {

	public StatusEvaluation {
		Objects.requireNonNull(status, "status");
	}

}
