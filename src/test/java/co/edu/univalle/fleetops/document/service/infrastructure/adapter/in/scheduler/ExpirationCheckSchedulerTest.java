package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.scheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.scheduling.TaskScheduler;

import co.edu.univalle.fleetops.document.service.application.port.in.CheckExpirationsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult.DocumentFailure;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest.CorrelationIdFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpirationCheckSchedulerTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

	private static final UUID RUN_ID = UUID.fromString("019aaa00-0000-7000-8000-0000000000a1");

	@Mock
	private CheckExpirationsUseCase useCase;

	@Mock
	private TaskScheduler taskScheduler;

	private ExpirationCheckScheduler scheduler() {
		return new ExpirationCheckScheduler(this.useCase, this.taskScheduler);
	}

	@Test
	void should_run_the_daily_check() {
		// Arrange
		given(this.useCase.checkExpirations())
			.willReturn(new ExpirationCheckResult(RUN_ID, TODAY, true, 10, 3, List.of()));

		// Act
		scheduler().runDaily();

		// Assert
		verify(this.useCase).checkExpirations();
	}

	@Test
	void should_recover_on_startup_in_the_background() {
		// Arrange (criterio 5)
		given(this.useCase.checkExpirations()).willReturn(ExpirationCheckResult.alreadyRan(TODAY));

		// Act
		scheduler().recoverOnStartup();

		// Assert: no corre en el hilo del arranque, sino en el planificador
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(task.capture(), any(Instant.class));
		verify(this.useCase, times(0)).checkExpirations();
		task.getValue().run();
		verify(this.useCase).checkExpirations();
	}

	@Test
	void should_log_each_failed_document_with_the_run_as_correlation_and_clean_the_mdc() {
		// Arrange (criterio 7)
		AtomicReference<String> correlationDuringRun = new AtomicReference<>();
		UUID failedDocument = UUID.fromString("019a2f4e-0000-7000-8000-000000000001");
		willAnswer(invocation -> {
			correlationDuringRun.set(MDC.get(CorrelationIdFilter.MDC_KEY));
			return new ExpirationCheckResult(RUN_ID, TODAY, true, 2, 1,
					List.of(new DocumentFailure(failedDocument, "IllegalStateException: bloqueo")));
		}).given(this.useCase).checkExpirations();

		// Act
		scheduler().run("prueba");

		// Assert
		assertThat(correlationDuringRun.get()).isNull();
		assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void should_never_propagate_a_failure() {
		given(this.useCase.checkExpirations()).willThrow(new IllegalStateException("base de datos caída"));

		assertThatCode(() -> scheduler().run("prueba")).doesNotThrowAnyException();
	}

	@Test
	void should_reject_missing_dependencies() {
		assertThatNullPointerException().isThrownBy(() -> new ExpirationCheckScheduler(null, this.taskScheduler))
			.withMessage("useCase");
		assertThatNullPointerException().isThrownBy(() -> new ExpirationCheckScheduler(this.useCase, null))
			.withMessage("taskScheduler");
	}

}
