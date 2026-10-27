package co.edu.univalle.fleetops.document.service.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DocumentTypeRulesTest {

	@Test
	void should_keep_catalog_rules_when_created() {
		// Act
		DocumentTypeRules rules = new DocumentTypeRules(DocumentTypeCode.PROPERTY_CARD, OwnerType.VEHICLE, false,
				true, false, true);

		// Assert
		assertThat(rules.code()).isEqualTo(DocumentTypeCode.PROPERTY_CARD);
		assertThat(rules.ownerType()).isEqualTo(OwnerType.VEHICLE);
		assertThat(rules.hasExpiration()).isFalse();
		assertThat(rules.mandatory()).isTrue();
		assertThat(rules.renewable()).isFalse();
		assertThat(rules.active()).isTrue();
	}

	@Test
	void should_reject_missing_code() {
		assertThatNullPointerException()
			.isThrownBy(() -> new DocumentTypeRules(null, OwnerType.VEHICLE, true, true, true, true))
			.withMessage("code");
	}

	@Test
	void should_reject_missing_owner_type() {
		assertThatNullPointerException()
			.isThrownBy(() -> new DocumentTypeRules(DocumentTypeCode.SOAT, null, true, true, true, true))
			.withMessage("ownerType");
	}

}
