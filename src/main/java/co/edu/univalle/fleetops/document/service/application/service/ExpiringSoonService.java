package co.edu.univalle.fleetops.document.service.application.service;

import java.time.LocalDate;
import java.util.Objects;

import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonView;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonView.ExpiringDocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.PageResult;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository.ExpiringDocumentSnapshot;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;
import co.edu.univalle.fleetops.document.service.domain.service.StatusEvaluation;

/**
 * Implementación de la consulta de documentos próximos a vencer (HU-DOC-08).
 *
 * <p>Busca por fecha de vencimiento y no por el estado guardado, porque ese estado puede estar desactualizado si el
 * job diario no ha corrido. Por eso el estado y los días restantes se recalculan con la fecha de hoy (ADR-DOC-01).
 */
public final class ExpiringSoonService implements ExpiringSoonUseCase {

	private final DocumentRepository documents;

	private final BusinessClock clock;

	private final DocumentStatusCalculator statusCalculator;

	public ExpiringSoonService(DocumentRepository documents, BusinessClock clock,
			DocumentStatusCalculator statusCalculator) {
		this.documents = Objects.requireNonNull(documents, "documents");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.statusCalculator = Objects.requireNonNull(statusCalculator, "statusCalculator");
	}

	@Override
	public ExpiringSoonView findExpiringSoon(ExpiringSoonQuery query) {
		Objects.requireNonNull(query, "query");
		LocalDate today = this.clock.today();
		PageResult<ExpiringDocumentView> page = this.documents
			.findExpiringBetween(today, today.plusDays(query.days()), query.documentType(), query.ownerType(),
					query.pageQuery())
			.map(snapshot -> toView(snapshot, today));
		return new ExpiringSoonView(query.days(), today, page);
	}

	private ExpiringDocumentView toView(ExpiringDocumentSnapshot snapshot, LocalDate today) {
		StatusEvaluation evaluation = this.statusCalculator.evaluate(snapshot.expirationDate(), today);
		return new ExpiringDocumentView(snapshot.documentId(), snapshot.documentType(), snapshot.ownerType(),
				snapshot.ownerId(), snapshot.documentNumber(), snapshot.licensePlate(), snapshot.fullName(),
				snapshot.expirationDate(), evaluation.daysRemaining(), evaluation.status());
	}

}
