package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.out.AuditLogRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.CorrelationIdProvider;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentTypeRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OwnerReplicaRepository;
import co.edu.univalle.fleetops.document.service.application.service.RegisterDocumentService;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.clock.ZonedBusinessClock;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.id.UuidV7Generator;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.observability.MdcCorrelationIdProvider;

/**
 * Composition Root: cablea de forma explícita los puertos con sus adaptadores y los casos de uso con sus
 * decoradores transaccionales. Los adaptadores de persistencia son componentes de Spring; los servicios de
 * aplicación son clases planas que solo se crean aquí.
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

	@Bean
	IdGenerator idGenerator(Clock systemClock) {
		return new UuidV7Generator(systemClock, new SecureRandom());
	}

	@Bean
	CorrelationIdProvider correlationIdProvider() {
		return new MdcCorrelationIdProvider();
	}

	/**
	 * Regla única de estado con el umbral mayor de ALERT_THRESHOLD_DAYS (ADR-DOC-01, ADR-DOC-09).
	 */
	@Bean
	DocumentStatusCalculator documentStatusCalculator(DocumentServiceProperties properties) {
		return new DocumentStatusCalculator(properties.maxAlertThresholdDays());
	}

	/**
	 * HU-DOC-01 y HU-DOC-02. Solo se expone el decorador transaccional como implementación del puerto.
	 */
	@Bean
	RegisterDocumentUseCase registerDocumentUseCase(DocumentTypeRepository documentTypes,
			OwnerReplicaRepository owners, DocumentRepository documents, AuditLogRepository auditLog,
			OutboxRepository outbox, IdGenerator idGenerator, CorrelationIdProvider correlationIds,
			BusinessClock businessClock, DocumentStatusCalculator statusCalculator,
			PlatformTransactionManager transactionManager) {
		RegisterDocumentService service = new RegisterDocumentService(documentTypes, owners, documents, auditLog,
				outbox, idGenerator, correlationIds, businessClock, statusCalculator);
		return new TransactionalRegisterDocumentUseCase(service, new TransactionTemplate(transactionManager));
	}

}
