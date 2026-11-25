package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.univalle.fleetops.document.service.application.port.in.CheckExpirationsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ComplianceUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase;
import co.edu.univalle.fleetops.document.service.application.port.out.EligibleOwnerRepository;
import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.RegisterDocumentUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.SyncOwnerReplicaUseCase;
import co.edu.univalle.fleetops.document.service.application.port.out.AuditLogRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.CorrelationIdProvider;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentTypeRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.application.port.out.IssuedAlertRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.JobRunRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OwnerReplicaRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.ProcessedEventRepository;
import co.edu.univalle.fleetops.document.service.application.service.CheckExpirationsService;
import co.edu.univalle.fleetops.document.service.application.service.ComplianceService;
import co.edu.univalle.fleetops.document.service.application.service.ValidityCheckService;
import co.edu.univalle.fleetops.document.service.application.service.EvaluateDocumentExpirationService;
import co.edu.univalle.fleetops.document.service.application.service.RegisterDocumentService;
import co.edu.univalle.fleetops.document.service.application.service.SyncOwnerReplicaService;
import co.edu.univalle.fleetops.document.service.domain.service.AlertThresholdPolicy;
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
@EnableConfigurationProperties({ DocumentServiceProperties.class, ExpirationJobProperties.class })
public class CoreConfiguration {

	@Bean
	ComplianceUseCase complianceUseCase(OwnerReplicaRepository owners, DocumentRepository documents,
			BusinessClock businessClock, DocumentStatusCalculator statusCalculator) {
		return new ComplianceService(owners, documents, businessClock, statusCalculator);
	}

	/**
	 * HU-DOC-12: vigencia de la documentación obligatoria hasta una fecha. Solo lectura, sin transacción explícita.
	 */
	@Bean
	ValidityCheckUseCase validityCheckUseCase(EligibleOwnerRepository eligibleOwners, BusinessClock businessClock) {
		return new ValidityCheckService(eligibleOwners, businessClock);
	}

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

	/**
	 * HU-DOC-10. Existe aunque la mensajería esté apagada: lo invocan los consumidores SQS de
	 * {@link MessagingConfiguration}.
	 */
	@Bean
	SyncOwnerReplicaUseCase syncOwnerReplicaUseCase(OwnerReplicaRepository owners,
			ProcessedEventRepository processedEvents, BusinessClock businessClock,
			PlatformTransactionManager transactionManager) {
		SyncOwnerReplicaService service = new SyncOwnerReplicaService(owners, processedEvents, businessClock);
		return new TransactionalSyncOwnerReplicaUseCase(service, new TransactionTemplate(transactionManager));
	}

	/**
	 * Umbrales de ALERT_THRESHOLD_DAYS para el job de vigencia (ADR-DOC-03, ADR-DOC-09).
	 */
	@Bean
	AlertThresholdPolicy alertThresholdPolicy(DocumentServiceProperties properties) {
		return new AlertThresholdPolicy(properties.alertThresholdDays());
	}

	/**
	 * HU-DOC-06: evaluación de un documento, en su propia transacción (decorador).
	 */
	@Bean
	EvaluateDocumentExpirationUseCase evaluateDocumentExpirationUseCase(DocumentRepository documents,
			IssuedAlertRepository issuedAlerts, AuditLogRepository auditLog, OutboxRepository outbox,
			BusinessClock businessClock, DocumentStatusCalculator statusCalculator, AlertThresholdPolicy alertPolicy,
			PlatformTransactionManager transactionManager) {
		EvaluateDocumentExpirationService service = new EvaluateDocumentExpirationService(documents, issuedAlerts,
				auditLog, outbox, businessClock, statusCalculator, alertPolicy);
		return new TransactionalEvaluateDocumentExpirationUseCase(service,
				new TransactionTemplate(transactionManager));
	}

	/**
	 * HU-DOC-06: verificación diaria. No es transaccional: cada documento se confirma por separado. Existe aunque el
	 * job esté apagado; lo programa {@link ExpirationJobConfiguration}.
	 */
	@Bean
	CheckExpirationsUseCase checkExpirationsUseCase(JobRunRepository jobRuns, DocumentRepository documents,
			EvaluateDocumentExpirationUseCase evaluator, BusinessClock businessClock,
			AlertThresholdPolicy alertPolicy) {
		return new CheckExpirationsService(jobRuns, documents, evaluator, businessClock, alertPolicy);
	}

}
