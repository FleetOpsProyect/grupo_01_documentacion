package co.edu.univalle.fleetops.document.service.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.application.port.in.PageQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.PageResult;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Puerto de salida: persistencia de documentos (Repository).
 */
public interface DocumentRepository {

	/**
	 * La combinación tipo + número es única entre los documentos que reservan su número, es decir, los no anulados
	 * ({@link co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus#reservesDocumentNumber()},
	 * DAS sec. 6.3).
	 */
	boolean existsNotCancelledByTypeAndNumber(DocumentTypeCode documentType, DocumentNumber documentNumber);

	/**
	 * Un propietario tiene como máximo un documento activo (VALID, EXPIRING_SOON o EXPIRED) por tipo (ADR-DOC-11).
	 */
	boolean existsActiveByOwnerAndType(OwnerRef owner, DocumentTypeCode documentType);

	/**
	 * Guarda un documento nuevo.
	 * @throws co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException si la base de
	 * datos rechaza el documento por unicidad, por ejemplo con dos registros simultáneos
	 */
	void save(Document document);

	/**
	 * Busca un documento por su identificador.
	 * Incluye documentos REPLACED y CANCELLED.
	 *
	 * @param documentId identificador del documento
	 * @return documento encontrado, si existe
	 */
	Optional<Document> findById(UUID documentId);

	/**
	 * Documentos vigentes o vencidos del propietario, excluyendo REPLACED y CANCELLED.
	 */
	List<ComplianceDocumentSnapshot> findComplianceDocuments(OwnerRef owner);

	record ComplianceDocumentSnapshot(UUID documentId, DocumentTypeCode documentType, LocalDate expirationDate) {
	}

	/**
	 * Documentos que el job de vigencia debe evaluar hoy (HU-DOC-06): VALID o EXPIRING_SOON, con fecha de
	 * vencimiento (nunca PROPERTY_CARD), no vencidos y dentro del umbral mayor, o EXPIRING_SOON fuera de él (por
	 * ejemplo, si se bajó el umbral). Excluye los documentos de propietarios dados de baja en la réplica: ya no
	 * requieren seguimiento (HU-DOC-10). Los vencidos los atiende HU-DOC-07.
	 *
	 * @param today fecha de negocio
	 * @param alertHorizon today + umbral mayor de ALERT_THRESHOLD_DAYS
	 */
	List<UUID> findExpirationCandidateIds(LocalDate today, LocalDate alertHorizon);

	/**
	 * Lee el documento bloqueando su fila hasta el fin de la transacción.
	 *
	 * @return el documento si sigue VALID o EXPIRING_SOON con fecha de vencimiento; vacío si mientras tanto se
	 * renovó, se anuló o dejó de existir
	 */
	Optional<ExpirationCandidate> lockExpirationCandidate(UUID documentId);

	/**
	 * Guarda el estado calculado con la regla única (ADR-DOC-01) en la transacción en curso.
	 */
	void updateStatus(UUID documentId, DocumentStatus status, Instant updatedAt);

	/**
	 * Datos del documento que usa el job de vigencia.
	 */
	record ExpirationCandidate(UUID documentId, DocumentTypeCode documentType, OwnerRef owner,
							   LocalDate expirationDate, DocumentStatus status) {

		public ExpirationCandidate {
			Objects.requireNonNull(documentId, "documentId");
			Objects.requireNonNull(documentType, "documentType");
			Objects.requireNonNull(owner, "owner");
			Objects.requireNonNull(expirationDate, "expirationDate");
			Objects.requireNonNull(status, "status");
		}

	}

	/**
	 * Documentos activos que vencen entre {@code from} y {@code to}, ambos incluidos, ordenados por fecha de
	 * vencimiento (HU-DOC-08). Incluye la placa o el nombre del propietario.
	 *
	 * @param documentType filtro por tipo; null si no se filtra
	 * @param ownerType filtro por tipo de propietario; null si no se filtra
	 */
	PageResult<ExpiringDocumentSnapshot> findExpiringBetween(LocalDate from, LocalDate to,
			DocumentTypeCode documentType, OwnerType ownerType, PageQuery pageQuery);

	/**
	 * Datos de un documento próximo a vencer, tal como salen de la base de datos.
	 *
	 * @param licensePlate placa del vehículo; null en conductores o si la réplica no la tiene
	 * @param fullName nombre del conductor; null en vehículos o si la réplica no lo tiene
	 */
	record ExpiringDocumentSnapshot(UUID documentId, DocumentTypeCode documentType, OwnerType ownerType,
			UUID ownerId, String documentNumber, LocalDate expirationDate, String licensePlate, String fullName) {
	}

}
