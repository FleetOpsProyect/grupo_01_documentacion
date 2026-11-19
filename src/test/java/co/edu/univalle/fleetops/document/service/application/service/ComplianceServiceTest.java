package co.edu.univalle.fleetops.document.service.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.application.port.in.ComplianceView;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OwnerReplicaRepository;
import co.edu.univalle.fleetops.document.service.domain.exception.ResourceNotFoundException;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ComplianceServiceTest {

	private static final UUID VEHICLE_ID =
		UUID.fromString("01960000-0000-7000-8000-000000000001");

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

	private final OwnerReplicaRepository owners = mock(OwnerReplicaRepository.class);

	private final DocumentRepository documents = mock(DocumentRepository.class);

	private final BusinessClock clock = mock(BusinessClock.class);

	private final DocumentStatusCalculator calculator = new DocumentStatusCalculator(30);

	private final ComplianceService service =
		new ComplianceService(this.owners, this.documents, this.clock, this.calculator);

	@Test
	void should_report_not_found_when_owner_does_not_exist() {
		OwnerRef owner = new OwnerRef(OwnerType.VEHICLE, VEHICLE_ID);
		given(this.owners.existsOwner(owner)).willReturn(false);

		assertThatThrownBy(() -> this.service.evaluate(owner))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Propietario no encontrado");
	}

	@Test
	void should_report_compliant_vehicle_with_three_valid_documents() {
		OwnerRef owner = new OwnerRef(OwnerType.VEHICLE, VEHICLE_ID);
		given(this.owners.existsOwner(owner)).willReturn(true);
		given(this.clock.today()).willReturn(TODAY);
		given(this.documents.findComplianceDocuments(owner)).willReturn(List.of(
				new DocumentRepository.ComplianceDocumentSnapshot(
						UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f"),
						DocumentTypeCode.SOAT, TODAY.plusDays(100)),
				new DocumentRepository.ComplianceDocumentSnapshot(
						UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e70"),
						DocumentTypeCode.TECHNICAL_INSPECTION, TODAY.plusDays(20)),
				new DocumentRepository.ComplianceDocumentSnapshot(
						UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e71"),
						DocumentTypeCode.PROPERTY_CARD, null)));

		ComplianceView view = this.service.evaluate(owner);

		assertThat(view.compliant()).isTrue();
		assertThat(view.missingDocuments()).isEmpty();
		assertThat(view.documents()).hasSize(3);
	}

	@Test
	void should_mark_expired_and_missing_vehicle_documents() {
		OwnerRef owner = new OwnerRef(OwnerType.VEHICLE, VEHICLE_ID);
		given(this.owners.existsOwner(owner)).willReturn(true);
		given(this.clock.today()).willReturn(TODAY);
		given(this.documents.findComplianceDocuments(owner)).willReturn(List.of(
				new DocumentRepository.ComplianceDocumentSnapshot(
						UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f"),
						DocumentTypeCode.SOAT, TODAY.minusDays(1))));

		ComplianceView view = this.service.evaluate(owner);

		assertThat(view.compliant()).isFalse();
		assertThat(view.missingDocuments()).containsExactlyInAnyOrder(
				DocumentTypeCode.SOAT, DocumentTypeCode.TECHNICAL_INSPECTION,
				DocumentTypeCode.PROPERTY_CARD);
		assertThat(view.documents()).hasSize(1);
		assertThat(view.documents().getFirst().daysRemaining()).isEqualTo(-1);
	}

}
