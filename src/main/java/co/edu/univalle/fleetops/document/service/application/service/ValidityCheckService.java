package co.edu.univalle.fleetops.document.service.application.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import co.edu.univalle.fleetops.document.service.application.port.in.ValidityCheckUseCase;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.EligibleOwnerRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.EligibleOwnerRepository.EligibleOwner;
import co.edu.univalle.fleetops.document.service.application.port.out.EligibleOwnerRepository.EligibleOwners;
import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Application Service: verificación de vigencia hasta una fecha (HU-DOC-12).
 *
 * <ol>
 * <li>valid_until no puede ser anterior a la fecha de negocio (America/Bogota): 422 (criterio 4).</li>
 * <li>Pide al repositorio la página de propietarios aptos (criterio 2).</li>
 * <li>Arma la respuesta: placa para vehículos, nombre para conductores, documentos en el orden del catálogo y el
 * total de páginas (criterio 3).</li>
 * </ol>
 *
 * <p>Solo lectura: no modifica datos ni publica eventos (criterio 5).
 */
public final class ValidityCheckService implements ValidityCheckUseCase {

	private final EligibleOwnerRepository eligibleOwners;

	private final BusinessClock clock;

	public ValidityCheckService(EligibleOwnerRepository eligibleOwners, BusinessClock clock) {
		this.eligibleOwners = Objects.requireNonNull(eligibleOwners, "eligibleOwners");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public ValidityCheckPage check(ValidityCheckQuery query) {
		Objects.requireNonNull(query, "query");
		LocalDate today = this.clock.today();
		if (query.validUntil().isBefore(today)) {
			throw new BusinessRuleViolationException(
					"valid_until no puede ser anterior a la fecha actual (" + today + ").");
		}
		long offset = (long) query.page() * query.size();
		EligibleOwners result = this.eligibleOwners.findEligible(query.ownerType(), query.validUntil(),
				query.ownerIds(), offset, query.size());
		List<EligibleOwnerView> data = result.owners()
			.stream()
			.map(owner -> toView(query.ownerType(), owner))
			.toList();
		int totalPages = (int) ((result.total() + query.size() - 1) / query.size());
		return new ValidityCheckPage(query.page(), query.size(), result.total(), totalPages, data);
	}

	private static EligibleOwnerView toView(OwnerType ownerType, EligibleOwner owner) {
		List<ValidDocumentView> documents = owner.documents()
			.stream()
			.sorted(Comparator.comparing(EligibleOwnerRepository.ValidDocument::documentType))
			.map(document -> new ValidDocumentView(document.documentType(), document.expirationDate()))
			.toList();
		boolean vehicle = ownerType == OwnerType.VEHICLE;
		return new EligibleOwnerView(owner.ownerId(), vehicle ? owner.label() : null, vehicle ? null : owner.label(),
				documents);
	}

}
