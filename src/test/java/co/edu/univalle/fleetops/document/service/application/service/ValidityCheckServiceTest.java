package co.edu.univalle.fleetops.document.service.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.in.InvalidQueryParameterException;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase.EligibleOwnerView;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase.ValidDocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase.ValidityCheckPage;
import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase.ValidityCheckQuery;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.EligibleOwnerRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.EligibleOwnerRepository.EligibleOwner;
import co.edu.univalle.fleetops.document.service.application.port.out.EligibleOwnerRepository.EligibleOwners;
import co.edu.univalle.fleetops.document.service.application.port.out.EligibleOwnerRepository.ValidDocument;
import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ValidityCheckServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

	private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 12, 31);

	private static final UUID VEHICLE_ID = UUID.fromString("01960000-0000-7000-8000-000000000001");

	private static final UUID DRIVER_ID = UUID.fromString("01960000-0000-7000-8000-000000000101");

	@Mock
	private EligibleOwnerRepository eligibleOwners;

	@Mock
	private BusinessClock clock;

	private ValidityCheckService service() {
		return new ValidityCheckService(this.eligibleOwners, this.clock);
	}

	private static ValidityCheckQuery query(OwnerType ownerType, LocalDate validUntil, int page, int size) {
		return new ValidityCheckQuery(ownerType, validUntil, Set.of(), page, size);
	}

	@Test
	void should_return_the_eligible_vehicles_with_their_plate_and_documents_in_catalog_order() {
		// Arrange: el repositorio devuelve los documentos en cualquier orden
		given(this.clock.today()).willReturn(TODAY);
		given(this.eligibleOwners.findEligible(OwnerType.VEHICLE, VALID_UNTIL, Set.of(), 0, 20))
			.willReturn(new EligibleOwners(1, List.of(new EligibleOwner(VEHICLE_ID, "ABC123",
					List.of(new ValidDocument(DocumentTypeCode.PROPERTY_CARD, null),
							new ValidDocument(DocumentTypeCode.SOAT, LocalDate.of(2027, 9, 30)),
							new ValidDocument(DocumentTypeCode.TECHNICAL_INSPECTION, LocalDate.of(2027, 3, 15)))))));

		// Act
		ValidityCheckPage page = service().check(query(OwnerType.VEHICLE, VALID_UNTIL, 0, 20));

		// Assert (criterio 3)
		assertThat(page).isEqualTo(new ValidityCheckPage(0, 20, 1, 1, List.of(new EligibleOwnerView(VEHICLE_ID,
				"ABC123", null,
				List.of(new ValidDocumentView(DocumentTypeCode.SOAT, LocalDate.of(2027, 9, 30)),
						new ValidDocumentView(DocumentTypeCode.TECHNICAL_INSPECTION, LocalDate.of(2027, 3, 15)),
						new ValidDocumentView(DocumentTypeCode.PROPERTY_CARD, null))))));
	}

	@Test
	void should_return_the_eligible_drivers_with_their_name() {
		given(this.clock.today()).willReturn(TODAY);
		given(this.eligibleOwners.findEligible(OwnerType.DRIVER, TODAY, Set.of(), 0, 20))
			.willReturn(new EligibleOwners(1, List.of(new EligibleOwner(DRIVER_ID, "Ana María Restrepo",
					List.of(new ValidDocument(DocumentTypeCode.DRIVER_LICENSE, LocalDate.of(2030, 1, 1)))))));

		// valid_until igual a la fecha actual es válido
		EligibleOwnerView driver = service().check(query(OwnerType.DRIVER, TODAY, 0, 20)).data().getFirst();

		assertThat(driver.licensePlate()).isNull();
		assertThat(driver.fullName()).isEqualTo("Ana María Restrepo");
	}

	@Test
	void should_compute_the_offset_and_the_total_pages() {
		// Arrange: 41 aptos en páginas de 20; se pide la tercera
		given(this.clock.today()).willReturn(TODAY);
		given(this.eligibleOwners.findEligible(OwnerType.VEHICLE, VALID_UNTIL, Set.of(), 40, 20))
			.willReturn(new EligibleOwners(41, List.of(new EligibleOwner(VEHICLE_ID, "ABC123", List.of()))));

		// Act
		ValidityCheckPage page = service().check(query(OwnerType.VEHICLE, VALID_UNTIL, 2, 20));

		// Assert
		assertThat(page.totalElements()).isEqualTo(41);
		assertThat(page.totalPages()).isEqualTo(3);
		assertThat(page.data()).hasSize(1);
	}

	@Test
	void should_return_an_empty_page_when_nobody_is_eligible() {
		given(this.clock.today()).willReturn(TODAY);
		given(this.eligibleOwners.findEligible(OwnerType.VEHICLE, VALID_UNTIL, Set.of(), 0, 20))
			.willReturn(new EligibleOwners(0, List.of()));

		assertThat(service().check(query(OwnerType.VEHICLE, VALID_UNTIL, 0, 20)))
			.isEqualTo(new ValidityCheckPage(0, 20, 0, 0, List.of()));
	}

	@Test
	void should_not_overflow_the_offset_of_a_far_page() {
		given(this.clock.today()).willReturn(TODAY);
		long offset = (long) Integer.MAX_VALUE * 100;
		given(this.eligibleOwners.findEligible(OwnerType.VEHICLE, VALID_UNTIL, Set.of(), offset, 100))
			.willReturn(new EligibleOwners(1, List.of()));

		assertThat(service().check(query(OwnerType.VEHICLE, VALID_UNTIL, Integer.MAX_VALUE, 100)).data()).isEmpty();
	}

	@Test
	void should_reject_a_date_before_today_with_422() {
		// Arrange (criterio 4)
		given(this.clock.today()).willReturn(TODAY);
		ValidityCheckQuery yesterday = query(OwnerType.VEHICLE, TODAY.minusDays(1), 0, 20);

		// Act + Assert
		assertThatExceptionOfType(BusinessRuleViolationException.class).isThrownBy(() -> service().check(yesterday))
			.withMessage("valid_until no puede ser anterior a la fecha actual (2026-11-24).");
		verifyNoInteractions(this.eligibleOwners);
	}

	@Test
	void should_validate_the_query_parameters_with_400() {
		Set<UUID> none = Set.of();
		assertThatExceptionOfType(InvalidQueryParameterException.class)
			.isThrownBy(() -> new ValidityCheckQuery(null, VALID_UNTIL, none, 0, 20))
			.withMessage(ValidityCheckQuery.OWNER_TYPE_MESSAGE);
		assertThatExceptionOfType(InvalidQueryParameterException.class)
			.isThrownBy(() -> new ValidityCheckQuery(OwnerType.VEHICLE, null, none, 0, 20))
			.withMessage(ValidityCheckQuery.VALID_UNTIL_MESSAGE);
		assertThatExceptionOfType(InvalidQueryParameterException.class)
			.isThrownBy(() -> new ValidityCheckQuery(OwnerType.VEHICLE, VALID_UNTIL, none, -1, 20))
			.withMessage(ValidityCheckQuery.PAGE_MESSAGE);
		assertThatExceptionOfType(InvalidQueryParameterException.class)
			.isThrownBy(() -> new ValidityCheckQuery(OwnerType.VEHICLE, VALID_UNTIL, none, 0, 0))
			.withMessage(ValidityCheckQuery.SIZE_MESSAGE);
		assertThatExceptionOfType(InvalidQueryParameterException.class)
			.isThrownBy(() -> new ValidityCheckQuery(OwnerType.VEHICLE, VALID_UNTIL, none, 0, 101))
			.withMessage(ValidityCheckQuery.SIZE_MESSAGE);
		Set<UUID> tooMany = new java.util.HashSet<>();
		for (int i = 0; i <= ValidityCheckQuery.MAX_OWNER_IDS; i++) {
			tooMany.add(UUID.randomUUID());
		}
		assertThatExceptionOfType(InvalidQueryParameterException.class)
			.isThrownBy(() -> new ValidityCheckQuery(OwnerType.VEHICLE, VALID_UNTIL, tooMany, 0, 20))
			.withMessage(ValidityCheckQuery.OWNER_IDS_LIMIT_MESSAGE);
		assertThatNullPointerException()
			.isThrownBy(() -> new ValidityCheckQuery(OwnerType.VEHICLE, VALID_UNTIL, null, 0, 20))
			.withMessage("ownerIds");
	}

	@Test
	void should_accept_the_limits_of_the_query() {
		Set<UUID> hundred = new java.util.HashSet<>();
		for (int i = 0; i < ValidityCheckQuery.MAX_OWNER_IDS; i++) {
			hundred.add(UUID.randomUUID());
		}

		ValidityCheckQuery query = new ValidityCheckQuery(OwnerType.DRIVER, VALID_UNTIL, hundred, 0, 100);

		assertThat(query.ownerIds()).hasSize(100);
		assertThat(new ValidityCheckQuery(OwnerType.DRIVER, VALID_UNTIL, Set.of(), 0, 1).size()).isEqualTo(1);
	}

	@Test
	void should_validate_the_types_of_the_ports() {
		assertThatNullPointerException().isThrownBy(() -> new ValidityCheckPage(0, 20, 0, 0, null))
			.withMessage("data");
		assertThatNullPointerException().isThrownBy(() -> new EligibleOwnerView(null, "A", null, List.of()))
			.withMessage("ownerId");
		assertThatNullPointerException().isThrownBy(() -> new EligibleOwnerView(VEHICLE_ID, "A", null, null))
			.withMessage("documents");
		assertThatNullPointerException().isThrownBy(() -> new ValidDocumentView(null, null))
			.withMessage("documentType");
		assertThatNullPointerException().isThrownBy(() -> new EligibleOwners(0, null)).withMessage("owners");
		assertThatNullPointerException().isThrownBy(() -> new EligibleOwner(null, "A", List.of()))
			.withMessage("ownerId");
		assertThatNullPointerException().isThrownBy(() -> new EligibleOwner(VEHICLE_ID, "A", null))
			.withMessage("documents");
		assertThatNullPointerException().isThrownBy(() -> new ValidDocument(null, null))
			.withMessage("documentType");
	}

	@Test
	void should_reject_missing_dependencies_and_query() {
		assertThatNullPointerException().isThrownBy(() -> new ValidityCheckService(null, this.clock))
			.withMessage("eligibleOwners");
		assertThatNullPointerException().isThrownBy(() -> new ValidityCheckService(this.eligibleOwners, null))
			.withMessage("clock");
		assertThatNullPointerException().isThrownBy(() -> service().check(null)).withMessage("query");
	}

}
