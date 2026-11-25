package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.PublishPendingEventsUseCase.PublicationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransactionalPublishPendingEventsUseCaseTest {

	@Test
	void should_publish_each_batch_inside_a_transaction() {
		// Arrange
		PublishPendingEventsUseCase delegate = mock(PublishPendingEventsUseCase.class);
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		PublicationResult expected = new PublicationResult(3, List.of(), null, false);
		given(delegate.publishPending()).willReturn(expected);

		// Act
		PublicationResult result = new TransactionalPublishPendingEventsUseCase(delegate,
				new TransactionTemplate(transactionManager))
			.publishPending();

		// Assert
		assertThat(result).isEqualTo(expected);
		verify(transactionManager).commit(any());
	}

	@Test
	void should_reject_missing_dependencies() {
		TransactionTemplate template = new TransactionTemplate(mock(PlatformTransactionManager.class));
		PublishPendingEventsUseCase delegate = mock(PublishPendingEventsUseCase.class);
		assertThatNullPointerException()
			.isThrownBy(() -> new TransactionalPublishPendingEventsUseCase(null, template))
			.withMessage("delegate");
		assertThatNullPointerException()
			.isThrownBy(() -> new TransactionalPublishPendingEventsUseCase(delegate, null))
			.withMessage("transactionTemplate");
	}

}
