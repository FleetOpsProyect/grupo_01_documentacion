package co.edu.univalle.fleetops.document.service.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import co.edu.univalle.fleetops.document.service.application.port.in.ComplianceUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ComplianceView;
import co.edu.univalle.fleetops.document.service.application.port.in.ComplianceView.ComplianceDocumentView;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.OwnerReplicaRepository;
import co.edu.univalle.fleetops.document.service.domain.exception.ResourceNotFoundException;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;
import co.edu.univalle.fleetops.document.service.domain.service.StatusEvaluation;

/**
 * Evaluación de cumplimiento documental sin efectos secundarios (HU-DOC-09).
 */
public final class ComplianceService implements ComplianceUseCase {

	private final OwnerReplicaRepository owners;

	private final DocumentRepository documents;

	private final BusinessClock clock;

	private final DocumentStatusCalculator statusCalculator;

	public ComplianceService(OwnerReplicaRepository owners, DocumentRepository documents, BusinessClock clock,
			DocumentStatusCalculator statusCalculator) {
		this.owners = Objects.requireNonNull(owners, "owners");
		this.documents = Objects.requireNonNull(documents, "documents");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.statusCalculator = Objects.requireNonNull(statusCalculator, "statusCalculator");
	}

	@Override
	public ComplianceView evaluate(OwnerRef owner) {
		Objects.requireNonNull(owner, "owner");
		if (!this.owners.existsOwner(owner)) {
			throw new ResourceNotFoundException("Propietario no encontrado");
		}

		LocalDate today = this.clock.today();
		Map<DocumentTypeCode, ComplianceDocumentView> byType =
			new EnumMap<>(DocumentTypeCode.class);
		for (DocumentRepository.ComplianceDocumentSnapshot snapshot : this.documents.findComplianceDocuments(owner)) {
			StatusEvaluation evaluation = this.statusCalculator.evaluate(snapshot.expirationDate(), today);
			byType.put(snapshot.documentType(),
					new ComplianceDocumentView(snapshot.documentId(), snapshot.documentType(),
							evaluation.status(), snapshot.expirationDate(), evaluation.daysRemaining()));
		}

		List<DocumentTypeCode> mandatory = (owner.ownerType() == OwnerType.VEHICLE)
				? List.of(DocumentTypeCode.SOAT, DocumentTypeCode.TECHNICAL_INSPECTION,
						DocumentTypeCode.PROPERTY_CARD)
				: List.of(DocumentTypeCode.DRIVER_LICENSE);
		List<ComplianceDocumentView> resultDocuments = new ArrayList<>(byType.values())
			.stream()
			.filter(document -> mandatory.contains(document.documentType()))
			.sorted(java.util.Comparator.comparing(ComplianceDocumentView::documentType))
			.toList();
		List<DocumentTypeCode> missing = new ArrayList<>();
		boolean compliant = true;
		for (DocumentTypeCode type : mandatory) {
			ComplianceDocumentView document = byType.get(type);
			boolean valid = document != null
					&& (type == DocumentTypeCode.PROPERTY_CARD
						? document.status() == DocumentStatus.VALID
						: document.status() == DocumentStatus.VALID
							|| document.status() == DocumentStatus.EXPIRING_SOON);
			if (!valid) {
				compliant = false;
				missing.add(type);
			}
		}
		return new ComplianceView(owner.ownerType(), owner.ownerId(), today, compliant,
				List.copyOf(resultDocuments), List.copyOf(missing));
	}

}
