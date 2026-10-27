package co.edu.univalle.fleetops.document.service.domain.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;

/**
 * Domain Service: regla única de cálculo del estado por fecha (ADR-DOC-01, DAS sec. 6.2). La comparten el
 * registro, las consultas y el job, para que el estado sea el mismo en todos los caminos.
 *
 * <ul>
 * <li>days_remaining = expiration_date − fecha de negocio (America/Bogota, ADR-DOC-13).</li>
 * <li>days_remaining &lt; 0: EXPIRED.</li>
 * <li>Entre 0 y el umbral mayor de ALERT_THRESHOLD_DAYS, ambos incluidos: EXPIRING_SOON.</li>
 * <li>Mayor que el umbral mayor: VALID.</li>
 * <li>Sin fecha de vencimiento (PROPERTY_CARD): siempre VALID, sin days_remaining.</li>
 * </ul>
 */
public final class DocumentStatusCalculator {

	private final int maxThresholdDays;

	/**
	 * @param maxThresholdDays umbral mayor de alerta en días, frontera entre VALID y EXPIRING_SOON
	 */
	public DocumentStatusCalculator(int maxThresholdDays) {
		if (maxThresholdDays < 0) {
			throw new IllegalArgumentException("El umbral mayor de alerta no puede ser negativo");
		}
		this.maxThresholdDays = maxThresholdDays;
	}

	/**
	 * @param expirationDate fecha de vencimiento; vacía si el documento no vence
	 * @param today fecha de negocio
	 */
	public StatusEvaluation evaluate(LocalDate expirationDate, LocalDate today) {
		Objects.requireNonNull(today, "today");
		if (expirationDate == null) {
			return new StatusEvaluation(DocumentStatus.VALID, null);
		}
		int daysRemaining = Math.toIntExact(ChronoUnit.DAYS.between(today, expirationDate));
		if (daysRemaining < 0) {
			return new StatusEvaluation(DocumentStatus.EXPIRED, daysRemaining);
		}
		if (daysRemaining <= this.maxThresholdDays) {
			return new StatusEvaluation(DocumentStatus.EXPIRING_SOON, daysRemaining);
		}
		return new StatusEvaluation(DocumentStatus.VALID, daysRemaining);
	}

}
