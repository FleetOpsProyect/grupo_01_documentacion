package co.edu.univalle.fleetops.document.service.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

import co.edu.univalle.fleetops.document.service.application.port.in.CheckExpirationsUseCase;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.scheduler.ExpirationCheckScheduler;

/**
 * Programa el job de vigencia (HU-DOC-06). Solo se activa con {@code fleetops.documents.expiration-job.enabled=true}
 * (así está en application.yaml); las pruebas lo apagan y llaman el caso de uso directamente. Spring Boot crea el
 * {@link TaskScheduler} al habilitar la programación; su único hilo serializa la ejecución diaria y la del arranque.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("fleetops.documents.expiration-job.enabled")
@EnableScheduling
public class ExpirationJobConfiguration {

	@Bean
	ExpirationCheckScheduler expirationCheckScheduler(CheckExpirationsUseCase useCase, TaskScheduler taskScheduler) {
		return new ExpirationCheckScheduler(useCase, taskScheduler);
	}

}
