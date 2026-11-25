package co.edu.univalle.fleetops.document.service.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonView;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonView.ExpiringDocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.PageQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.PageResult;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository.ExpiringDocumentSnapshot;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExpiringSoonServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

	private static final UUID VEHICLE_ID = UUID.fromString("01960000-0000-7000-8000-000000000001");

	private static final UUID DRIVER_ID = UUID.fromString("01960000-0000-7000-8000-000000000101");

	private static final PageQuery FIRST_PAGE = new PageQuery(0, 20);

	private final DocumentRepository documents = mock(DocumentRepository.class);

	private final BusinessClock clock = mock(BusinessClock.class);

	private final ExpiringSoonService service =
		new ExpiringSoonService(this.documents, this.clock, new DocumentStatusCalculator(30));

	@Test
	void should_search_from_today_to_the_horizon_with_the_requested_filters_and_page() {
		// Arrange
		given(this.clock.today()).willReturn(TODAY);
		given(this.documents.findExpiringBetween(TODAY, TODAY.plusDays(15), DocumentTypeCode.SOAT,
				OwnerType.VEHICLE, FIRST_PAGE))
			.willReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

		// Act
		ExpiringSoonView view = this.service
			.findExpiringSoon(new ExpiringSoonQuery(15, DocumentTypeCode.SOAT, OwnerType.VEHICLE, FIRST_PAGE));

		// Assert: sin resultados es una página vacía, nunca un error (criterio 6)
		verify(this.documents).findExpiringBetween(TODAY, TODAY.plusDays(15), DocumentTypeCode.SOAT,
				OwnerType.VEHICLE, FIRST_PAGE);
		assertThat(view.horizonDays()).isEqualTo(15);
		assertThat(view.evaluatedOn()).isEqualTo(TODAY);
		assertThat(view.documents().content()).isEmpty();
		assertThat(view.documents().totalElements()).isZero();
	}

	@Test
	void should_recalculate_status_and_days_remaining_with_the_business_date() {
		// Arrange: el estado guardado no importa; se recalcula con la fecha (ADR-DOC-01)
		given(this.clock.today()).willReturn(TODAY);
		ExpiringDocumentSnapshot dueToday = new ExpiringDocumentSnapshot(UUID.randomUUID(),
				DocumentTypeCode.TECHNICAL_INSPECTION, OwnerType.VEHICLE, VEHICLE_ID, "RTM-554433", TODAY, "ABC123",
				null);
		ExpiringDocumentSnapshot license = new ExpiringDocumentSnapshot(UUID.randomUUID(),
				DocumentTypeCode.DRIVER_LICENSE, OwnerType.DRIVER, DRIVER_ID, "LIC-1020304050", TODAY.plusDays(25),
				null, "Conductor Demo Activo");
		ExpiringDocumentSnapshot beyondThreshold = new ExpiringDocumentSnapshot(UUID.randomUUID(),
				DocumentTypeCode.SOAT, OwnerType.VEHICLE, VEHICLE_ID, "AT-1300-0045-2026", TODAY.plusDays(45),
				"ABC123", null);
		given(this.documents.findExpiringBetween(TODAY, TODAY.plusDays(60), null, null, FIRST_PAGE))
			.willReturn(new PageResult<>(List.of(dueToday, license, beyondThreshold), 0, 20, 3, 1));

		// Act
		ExpiringSoonView view = this.service.findExpiringSoon(new ExpiringSoonQuery(60, null, null, FIRST_PAGE));

		// Assert
		List<ExpiringDocumentView> content = view.documents().content();
		assertThat(content).extracting(ExpiringDocumentView::daysRemaining).containsExactly(0, 25, 45);
		assertThat(content).extracting(ExpiringDocumentView::status)
			.containsExactly(DocumentStatus.EXPIRING_SOON, DocumentStatus.EXPIRING_SOON, DocumentStatus.VALID);
		assertThat(content.get(0)).extracting(ExpiringDocumentView::documentId, ExpiringDocumentView::documentType,
				ExpiringDocumentView::ownerType, ExpiringDocumentView::ownerId, ExpiringDocumentView::documentNumber,
				ExpiringDocumentView::licensePlate, ExpiringDocumentView::fullName,
				ExpiringDocumentView::expirationDate)
			.containsExactly(dueToday.documentId(), DocumentTypeCode.TECHNICAL_INSPECTION, OwnerType.VEHICLE,
					VEHICLE_ID, "RTM-554433", "ABC123", null, TODAY);
		assertThat(content.get(1).fullName()).isEqualTo("Conductor Demo Activo");
		assertThat(content.get(1).licensePlate()).isNull();
		assertThat(view.documents()).extracting(PageResult::page, PageResult::size, PageResult::totalElements,
				PageResult::totalPages)
			.containsExactly(0, 20, 3L, 1);
	}

	@Test
	void should_require_its_collaborators_and_the_query() {
		DocumentStatusCalculator calculator = new DocumentStatusCalculator(30);

		assertThatNullPointerException().isThrownBy(() -> new ExpiringSoonService(null, this.clock, calculator))
			.withMessage("documents");
		assertThatNullPointerException().isThrownBy(() -> new ExpiringSoonService(this.documents, null, calculator))
			.withMessage("clock");
		assertThatNullPointerException().isThrownBy(() -> new ExpiringSoonService(this.documents, this.clock, null))
			.withMessage("statusCalculator");
		assertThatNullPointerException().isThrownBy(() -> this.service.findExpiringSoon(null))
			.withMessage("query");
	}

}
