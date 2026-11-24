package co.edu.univalle.fleetops.document.service.domain.service;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Qué umbrales registrar para un documento en una ejecución del job (ADR-DOC-03).
 *
 * @param publishedThreshold umbral que se avisa con un evento; vacío si no hay nada que avisar
 * @param silentThresholds umbrales que se marcan como emitidos sin evento (issued_alerts.published = false)
 */
public record AlertDecision(OptionalInt publishedThreshold, List<Integer> silentThresholds) {

	private static final AlertDecision NONE = new AlertDecision(OptionalInt.empty(), List.of());

	public AlertDecision {
		Objects.requireNonNull(publishedThreshold, "publishedThreshold");
		silentThresholds = List.copyOf(Objects.requireNonNull(silentThresholds, "silentThresholds"));
	}

	public static AlertDecision none() {
		return NONE;
	}

	/**
	 * No hay umbrales nuevos que registrar.
	 */
	public boolean isEmpty() {
		return this.publishedThreshold.isEmpty() && this.silentThresholds.isEmpty();
	}

}
