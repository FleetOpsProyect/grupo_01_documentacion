package co.edu.univalle.fleetops.document.service.application.service;

import java.time.LocalDate;
import java.util.Objects;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.ListDocumentQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.ListDocumentsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.PageResult;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.service.AlertThresholdPolicy;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;
import co.edu.univalle.fleetops.document.service.domain.service.StatusEvaluation;

/**
 * Servicio de aplicación para listar documentos con filtros y paginación.
 * HU-DOC-03.
 */
public final class ListDocumentsService implements ListDocumentsUseCase {

	private final DocumentRepository documents;

	private final BusinessClock clock;

	private final DocumentStatusCalculator statusCalculator;

	private final AlertThresholdPolicy alertPolicy;

	public ListDocumentsService(DocumentRepository documents, BusinessClock clock,
								DocumentStatusCalculator statusCalculator, AlertThresholdPolicy alertPolicy) {

		this.documents = Objects.requireNonNull(documents, "documents");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.statusCalculator = Objects.requireNonNull(statusCalculator, "statusCalculator");
		this.alertPolicy = Objects.requireNonNull(alertPolicy, "alertPolicy");
	}

	@Override
	public PageResult<DocumentView> listDocuments(ListDocumentQuery query) {

		Objects.requireNonNull(query, "query");

		LocalDate today = this.clock.today();
		LocalDate alertHorizon = today.plusDays(this.alertPolicy.maxThresholdDays());

		return this.documents.findDocuments(query, today, alertHorizon)
				.map(document -> toView(document, today));
	}

	/**
	 * Construye la vista del documento con su estado actual.
	 *
	 * @param document documento del dominio
	 * @param today fecha de negocio
	 * @return vista del documento
	 */
	private DocumentView toView(Document document, LocalDate today) {

		StatusEvaluation evaluation;

		if (document.status() == DocumentStatus.REPLACED
				|| document.status() == DocumentStatus.CANCELLED) {

			evaluation = new StatusEvaluation(document.status(), null);
		}
		else {
			evaluation = this.statusCalculator.evaluate(
					document.validity().expirationDate(), today);
		}

		return DocumentView.of(document, evaluation);
	}

}
