package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.clock.ZonedBusinessClock;

/**
 * Composition Root: cablea de forma explícita los puertos con sus adaptadores. Aquí se registrarán también los
 * casos de uso y sus decoradores transaccionales.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DocumentServiceProperties.class)
public class CoreConfiguration {

	@Bean
	Clock systemClock() {
		return Clock.systemUTC();
	}

	@Bean
	BusinessClock businessClock(Clock systemClock, DocumentServiceProperties properties) {
		return new ZonedBusinessClock(systemClock, properties.businessTimeZone());
	}

}
