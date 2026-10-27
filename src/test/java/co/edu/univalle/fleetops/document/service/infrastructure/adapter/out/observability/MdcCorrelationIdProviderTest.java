package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.observability;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest.CorrelationIdFilter;

import static org.assertj.core.api.Assertions.assertThat;

class MdcCorrelationIdProviderTest {

	private final MdcCorrelationIdProvider provider = new MdcCorrelationIdProvider();

	@AfterEach
	void clearMdc() {
		MDC.remove(CorrelationIdFilter.MDC_KEY);
	}

	@Test
	void should_return_the_correlation_id_of_the_request() {
		// Arrange
		UUID correlationId = UUID.fromString("6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");
		MDC.put(CorrelationIdFilter.MDC_KEY, correlationId.toString());

		// Act + Assert
		assertThat(this.provider.currentCorrelationId()).contains(correlationId);
	}

	@Test
	void should_return_empty_outside_a_request() {
		assertThat(this.provider.currentCorrelationId()).isEmpty();
	}

	@Test
	void should_return_empty_when_the_value_is_not_a_uuid() {
		// Arrange
		MDC.put(CorrelationIdFilter.MDC_KEY, "not-a-uuid");

		// Act + Assert
		assertThat(this.provider.currentCorrelationId()).isEmpty();
	}

}
