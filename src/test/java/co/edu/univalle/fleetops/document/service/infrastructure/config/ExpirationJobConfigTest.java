package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.univalle.fleetops.document.service.application.port.in.CheckExpirationsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.EvaluateDocumentExpirationUseCase.DocumentExpirationOutcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Configuración del job de vigencia (HU-DOC-06): propiedades, identidad de la instancia, decorador transaccional y
 * creación del planificador.
 */
class ExpirationJobConfigTest {

	private static final Duration THIRTY_MINUTES = Duration.ofMinutes(30);

	@Test
	void should_accept_the_das_schedule() {
		ExpirationJobProperties properties = new ExpirationJobProperties(true, "0 0 1 * * *", THIRTY_MINUTES);

		assertThat(properties.enabled()).isTrue();
		assertThat(properties.staleAfter()).isEqualTo(THIRTY_MINUTES);
	}

	@Test
	void should_reject_an_invalid_cron_or_stale_time() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ExpirationJobProperties(true, null, THIRTY_MINUTES));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ExpirationJobProperties(true, "todos los días", THIRTY_MINUTES))
			.withMessageContaining("todos los días");
		assertThatIllegalArgumentException().isThrownBy(() -> new ExpirationJobProperties(true, "0 0 1 * * *", null));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ExpirationJobProperties(true, "0 0 1 * * *", Duration.ZERO));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ExpirationJobProperties(true, "0 0 1 * * *", Duration.ofMinutes(-1)));
	}

	@Test
	void should_identify_the_instance_by_host_and_pid() {
		assertThat(InstanceIdentity.of("documents-1", 42)).isEqualTo("documents-1/42");
		assertThat(InstanceIdentity.of(" ", 42)).isEqualTo(InstanceIdentity.UNKNOWN_HOST + "/42");
		assertThat(InstanceIdentity.of(null, 42)).isEqualTo(InstanceIdentity.UNKNOWN_HOST + "/42");
		assertThat(InstanceIdentity.of("h".repeat(200), 42)).hasSize(InstanceIdentity.MAX_LENGTH).endsWith("/42");
		assertThat(InstanceIdentity.current()).endsWith("/" + ProcessHandle.current().pid())
			.hasSizeLessThanOrEqualTo(InstanceIdentity.MAX_LENGTH);
	}

	@Test
	void should_fall_back_when_the_host_name_cannot_be_resolved() {
		assertThat(InstanceIdentity.hostName(() -> "documents-1")).isEqualTo("documents-1");
		assertThat(InstanceIdentity.hostName(() -> {
			throw new java.net.UnknownHostException("sin DNS");
		})).isEqualTo(InstanceIdentity.UNKNOWN_HOST);
	}

	@Test
	void should_evaluate_each_document_inside_a_transaction() {
		// Arrange
		EvaluateDocumentExpirationUseCase delegate = mock(EvaluateDocumentExpirationUseCase.class);
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		UUID documentId = UUID.randomUUID();
		UUID runId = UUID.randomUUID();
		LocalDate today = LocalDate.of(2026, 11, 23);
		given(delegate.evaluate(documentId, today, runId)).willReturn(DocumentExpirationOutcome.evaluated(true, true));
		TransactionalEvaluateDocumentExpirationUseCase useCase = new TransactionalEvaluateDocumentExpirationUseCase(
				delegate, new TransactionTemplate(transactionManager));

		// Act
		DocumentExpirationOutcome outcome = useCase.evaluate(documentId, today, runId);

		// Assert
		assertThat(outcome.alertPublished()).isTrue();
		verify(transactionManager).commit(any());
	}

	@Test
	void should_reject_a_decorator_without_dependencies() {
		TransactionTemplate template = new TransactionTemplate(mock(PlatformTransactionManager.class));
		EvaluateDocumentExpirationUseCase delegate = mock(EvaluateDocumentExpirationUseCase.class);
		assertThatNullPointerException()
			.isThrownBy(() -> new TransactionalEvaluateDocumentExpirationUseCase(null, template))
			.withMessage("delegate");
		assertThatNullPointerException()
			.isThrownBy(() -> new TransactionalEvaluateDocumentExpirationUseCase(delegate, null))
			.withMessage("transactionTemplate");
	}

	@Test
	void should_create_the_scheduler_bean() {
		assertThat(new ExpirationJobConfiguration().expirationCheckScheduler(mock(CheckExpirationsUseCase.class),
				mock(TaskScheduler.class))).isNotNull();
	}

}
