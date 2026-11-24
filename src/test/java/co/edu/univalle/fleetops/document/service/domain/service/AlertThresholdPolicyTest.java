package co.edu.univalle.fleetops.document.service.domain.service;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AlertThresholdPolicyTest {

	private final AlertThresholdPolicy policy = new AlertThresholdPolicy(List.of(7, 30, 1, 15));

	@Test
	void should_sort_the_thresholds_and_expose_the_largest() {
		assertThat(this.policy.maxThresholdDays()).isEqualTo(30);
	}

	@ParameterizedTest(name = "{0} días restantes: se avisa el umbral {1}")
	@CsvSource({ "30, 30", "29, 30", "15, 15", "8, 15", "7, 7", "1, 1", "0, 1" })
	void should_publish_the_threshold_just_reached_when_the_larger_ones_were_issued(int daysRemaining,
			int expected) {
		// Arrange: los umbrales mayores ya se emitieron en días anteriores
		Set<Integer> issued = Set.copyOf(List.of(30, 15, 7, 1).stream().filter(t -> t > expected).toList());

		// Act
		AlertDecision decision = this.policy.decide(daysRemaining, issued);

		// Assert
		assertThat(decision.publishedThreshold()).isEqualTo(OptionalInt.of(expected));
		assertThat(decision.silentThresholds()).isEmpty();
	}

	@Test
	void should_publish_nothing_before_the_largest_threshold() {
		AlertDecision decision = this.policy.decide(31, Set.of());

		assertThat(decision.isEmpty()).isTrue();
		assertThat(decision.publishedThreshold()).isEmpty();
	}

	@Test
	void should_publish_only_the_smallest_pending_threshold_and_silence_the_larger_ones() {
		// Arrange (ADR-DOC-03): el job no corrió y el documento ya está a 5 días
		// Act
		AlertDecision decision = this.policy.decide(5, Set.of());

		// Assert
		assertThat(decision.publishedThreshold()).isEqualTo(OptionalInt.of(7));
		assertThat(decision.silentThresholds()).containsExactly(30, 15);
		assertThat(decision.isEmpty()).isFalse();
	}

	@Test
	void should_publish_nothing_when_the_threshold_was_already_issued() {
		// Criterio 4: una segunda pasada el mismo día no emite nada
		assertThat(this.policy.decide(20, Set.of(30)).isEmpty()).isTrue();
	}

	@Test
	void should_not_publish_a_threshold_larger_than_one_already_issued() {
		// Arrange: se agregó el umbral 15 a la configuración cuando ya se había avisado el de 7
		// Act
		AlertDecision decision = this.policy.decide(6, Set.of(7));

		// Assert: no se avisa «15 días» después de «7 días»; se marca sin evento
		assertThat(decision.publishedThreshold()).isEmpty();
		assertThat(decision.silentThresholds()).containsExactly(30, 15);
	}

	@Test
	void should_leave_expired_documents_to_hu_doc_07() {
		assertThat(this.policy.decide(-1, Set.of()).isEmpty()).isTrue();
	}

	@Test
	void should_accept_zero_as_a_threshold() {
		AlertThresholdPolicy sameDay = new AlertThresholdPolicy(List.of(0));

		assertThat(sameDay.decide(0, Set.of()).publishedThreshold()).isEqualTo(OptionalInt.of(0));
		assertThat(sameDay.decide(1, Set.of()).isEmpty()).isTrue();
	}

	@Test
	void should_reject_an_invalid_configuration() {
		assertThatNullPointerException().isThrownBy(() -> new AlertThresholdPolicy(null)).withMessage("thresholds");
		assertThatIllegalArgumentException().isThrownBy(() -> new AlertThresholdPolicy(List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new AlertThresholdPolicy(List.of(30, -1)));
		assertThatIllegalArgumentException().isThrownBy(() -> new AlertThresholdPolicy(Arrays.asList(30, null)));
		assertThatIllegalArgumentException().isThrownBy(() -> new AlertThresholdPolicy(List.of(30, 30)));
	}

	@Test
	void should_reject_missing_issued_thresholds() {
		assertThatNullPointerException().isThrownBy(() -> this.policy.decide(5, null))
			.withMessage("issuedThresholds");
	}

	@Test
	void should_build_decisions_defensively() {
		assertThat(AlertDecision.none().isEmpty()).isTrue();
		assertThat(new AlertDecision(OptionalInt.empty(), List.of(30)).isEmpty()).isFalse();
		assertThatNullPointerException().isThrownBy(() -> new AlertDecision(null, List.of()))
			.withMessage("publishedThreshold");
		assertThatNullPointerException().isThrownBy(() -> new AlertDecision(OptionalInt.empty(), null))
			.withMessage("silentThresholds");
	}

}
