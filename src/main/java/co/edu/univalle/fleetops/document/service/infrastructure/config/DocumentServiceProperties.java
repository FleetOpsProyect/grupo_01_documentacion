package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de negocio del servicio, leídas de variables de entorno (DAS sec. 10).
 *
 * <p>Se validan al arrancar: una configuración inválida impide que el servicio inicie (ADR-DOC-09).
 *
 * @param businessTimeZone zona horaria de la «fecha actual» de negocio (BUSINESS_TIME_ZONE)
 * @param alertThresholdDays umbrales de alerta en días (ALERT_THRESHOLD_DAYS), ordenados de mayor a menor
 */
@ConfigurationProperties(prefix = "fleetops.documents")
public record DocumentServiceProperties(ZoneId businessTimeZone, List<Integer> alertThresholdDays) {

	public DocumentServiceProperties {
		if (businessTimeZone == null) {
			throw new IllegalArgumentException("BUSINESS_TIME_ZONE es obligatoria");
		}
		if (alertThresholdDays == null || alertThresholdDays.isEmpty()) {
			throw new IllegalArgumentException("ALERT_THRESHOLD_DAYS debe tener al menos un umbral");
		}
		if (alertThresholdDays.stream().anyMatch(days -> days == null || days < 0)) {
			throw new IllegalArgumentException("ALERT_THRESHOLD_DAYS solo admite enteros mayores o iguales a 0");
		}
		if (new HashSet<>(alertThresholdDays).size() != alertThresholdDays.size()) {
			throw new IllegalArgumentException("ALERT_THRESHOLD_DAYS no admite umbrales repetidos");
		}
		alertThresholdDays = alertThresholdDays.stream().sorted(Comparator.reverseOrder()).toList();
	}

	/**
	 * Umbral mayor: frontera entre VALID y EXPIRING_SOON en la regla única de estado (DAS sec. 6.2).
	 */
	public int maxAlertThresholdDays() {
		return this.alertThresholdDays.getFirst();
	}

}
