package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

/**
 * Propiedades del job de vigencia (HU-DOC-06, ADR-DOC-02), validadas al arrancar.
 *
 * @param enabled programa el job y la recuperación al arrancar; apagado en las pruebas
 * @param cron expresión cron de Spring, evaluada en BUSINESS_TIME_ZONE (DAS: 01:00 hora de Bogotá)
 * @param staleAfter tiempo tras el cual una ejecución que sigue en RUNNING se considera abandonada (la instancia
 * que la tenía se cayó) y otra instancia puede retomarla
 */
@ConfigurationProperties(prefix = "fleetops.documents.expiration-job")
public record ExpirationJobProperties(boolean enabled, String cron, Duration staleAfter) {

	public ExpirationJobProperties {
		if (cron == null || !CronExpression.isValidExpression(cron)) {
			throw new IllegalArgumentException(
					"fleetops.documents.expiration-job.cron no es una expresión cron válida: " + cron);
		}
		if (staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
			throw new IllegalArgumentException("fleetops.documents.expiration-job.stale-after debe ser mayor que cero");
		}
	}

}
