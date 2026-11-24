package co.edu.univalle.fleetops.document.service.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Domain Service: decide qué umbrales de ALERT_THRESHOLD_DAYS avisar (HU-DOC-06, ADR-DOC-03, ADR-DOC-09).
 *
 * <ul>
 * <li>Un umbral está alcanzado cuando days_remaining es igual o menor que él (y el documento no venció).</li>
 * <li>Si varios umbrales alcanzados siguen sin emitir, se avisa solo el menor y los mayores se marcan como emitidos
 * sin evento: tras una caída del job no llega una avalancha de avisos.</li>
 * <li>Un umbral mayor o igual que uno ya emitido no se avisa (sería un aviso más lejano que el ya enviado, por
 * ejemplo si se agrega un umbral a la configuración); también se marca sin evento.</li>
 * </ul>
 */
public final class AlertThresholdPolicy {

	private final List<Integer> thresholdsDescending;

	/**
	 * @param thresholds umbrales en días, enteros mayores o iguales a 0, sin repetidos, en cualquier orden
	 */
	public AlertThresholdPolicy(Collection<Integer> thresholds) {
		Objects.requireNonNull(thresholds, "thresholds");
		if (thresholds.isEmpty()) {
			throw new IllegalArgumentException("Debe haber al menos un umbral de alerta");
		}
		if (thresholds.stream().anyMatch(days -> days == null || days < 0)) {
			throw new IllegalArgumentException("Los umbrales de alerta deben ser enteros mayores o iguales a 0");
		}
		if (new HashSet<>(thresholds).size() != thresholds.size()) {
			throw new IllegalArgumentException("Los umbrales de alerta no pueden repetirse");
		}
		this.thresholdsDescending = thresholds.stream().sorted(Comparator.reverseOrder()).toList();
	}

	/**
	 * Umbral mayor: hasta cuántos días antes del vencimiento hay que evaluar un documento.
	 */
	public int maxThresholdDays() {
		return this.thresholdsDescending.getFirst();
	}

	/**
	 * @param daysRemaining días para el vencimiento según la fecha de negocio
	 * @param issuedThresholds umbrales ya registrados en issued_alerts para el documento
	 */
	public AlertDecision decide(int daysRemaining, Set<Integer> issuedThresholds) {
		Objects.requireNonNull(issuedThresholds, "issuedThresholds");
		if (daysRemaining < 0) {
			// Vencido: lo atiende HU-DOC-07 con document_expired_event
			return AlertDecision.none();
		}
		int smallestIssued = issuedThresholds.stream().mapToInt(Integer::intValue).min().orElse(Integer.MAX_VALUE);
		List<Integer> pending = this.thresholdsDescending.stream()
			.filter(threshold -> daysRemaining <= threshold)
			.filter(threshold -> !issuedThresholds.contains(threshold))
			.toList();
		OptionalInt published = pending.stream()
			.mapToInt(Integer::intValue)
			.filter(threshold -> threshold < smallestIssued)
			.min();
		List<Integer> silent = new ArrayList<>(pending);
		published.ifPresent(threshold -> silent.remove(Integer.valueOf(threshold)));
		return new AlertDecision(published, silent);
	}

}
