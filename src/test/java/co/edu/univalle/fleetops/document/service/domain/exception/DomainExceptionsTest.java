package co.edu.univalle.fleetops.document.service.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionsTest {

	@Test
	void should_keep_the_message_of_a_business_rule_violation() {
		// Act
		DomainException ex = new BusinessRuleViolationException("regla");

		// Assert
		assertThat(ex).hasMessage("regla").isInstanceOf(RuntimeException.class);
	}

	@Test
	void should_keep_the_message_of_a_conflict() {
		// Act
		DomainException ex = new DomainConflictException("conflicto");

		// Assert
		assertThat(ex).hasMessage("conflicto");
	}

	@Test
	void should_keep_the_message_of_a_missing_resource() {
		// Act
		DomainException ex = new ResourceNotFoundException("no existe");

		// Assert
		assertThat(ex).hasMessage("no existe");
	}

}
