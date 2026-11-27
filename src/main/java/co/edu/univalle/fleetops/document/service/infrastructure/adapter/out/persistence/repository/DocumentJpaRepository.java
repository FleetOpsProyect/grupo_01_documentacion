package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;

public interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {

	boolean existsByDocumentTypeAndDocumentNumberAndStatusIn(String documentType, String documentNumber,
			Collection<String> statuses);

	boolean existsByOwnerTypeAndOwnerIdAndDocumentTypeAndStatusIn(String ownerType, UUID ownerId,
			String documentType, Collection<String> statuses);

	List<DocumentJpaEntity> findByOwnerTypeAndOwnerIdAndStatusIn(String ownerType, UUID ownerId,
			Collection<String> statuses);

	/**
	 * Candidatos del job de vigencia (HU-DOC-06). Usa el índice parcial ix_documents_active_expiration. Excluye los
	 * documentos cuyo propietario está dado de baja en la réplica (HU-DOC-10); un propietario que no está en la
	 * réplica no se excluye.
	 */
	@Query("""
			SELECT d.documentId FROM DocumentJpaEntity d
			WHERE d.status IN ('VALID', 'EXPIRING_SOON')
			  AND d.expirationDate IS NOT NULL
			  AND d.expirationDate >= :today
			  AND (d.expirationDate <= :alertHorizon OR d.status = 'EXPIRING_SOON')
			  AND NOT EXISTS (SELECT 1 FROM VehicleReplicaJpaEntity v
			                  WHERE d.ownerType = 'VEHICLE' AND v.vehicleId = d.ownerId AND v.active = false)
			  AND NOT EXISTS (SELECT 1 FROM DriverReplicaJpaEntity r
			                  WHERE d.ownerType = 'DRIVER' AND r.driverId = d.ownerId AND r.active = false)
			ORDER BY d.expirationDate, d.documentId""")
	List<UUID> findExpirationCandidateIds(@Param("today") LocalDate today,
			@Param("alertHorizon") LocalDate alertHorizon);

	/**
	 * SELECT ... FOR UPDATE: el documento no cambia mientras el job lo evalúa.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT d FROM DocumentJpaEntity d WHERE d.documentId = :documentId")
	Optional<DocumentJpaEntity> findForUpdate(@Param("documentId") UUID documentId);

	/**
	 * Documentos próximos a vencer (HU-DOC-08), con la placa o el nombre de la réplica.
	 *
	 * <ul>
	 * <li>Busca por fecha, no por el estado guardado. {@code statuses} solo sirve para descartar REPLACED y
	 * CANCELLED.</li>
	 * <li>Si un filtro llega null, no se aplica.</li>
	 * <li>Se ordena también por document_id para que las páginas no cambien entre consultas cuando varios documentos
	 * vencen el mismo día.</li>
	 * </ul>
	 */
	@Query(value = """
			SELECT d.documentId AS documentId, d.documentType AS documentType, d.ownerType AS ownerType,
			       d.ownerId AS ownerId, d.documentNumber AS documentNumber, d.expirationDate AS expirationDate,
			       v.licensePlate AS licensePlate, r.fullName AS fullName
			FROM DocumentJpaEntity d
			LEFT JOIN VehicleReplicaJpaEntity v ON d.ownerType = 'VEHICLE' AND v.vehicleId = d.ownerId
			LEFT JOIN DriverReplicaJpaEntity r ON d.ownerType = 'DRIVER' AND r.driverId = d.ownerId
			WHERE d.status IN :statuses
			  AND d.expirationDate IS NOT NULL
			  AND d.expirationDate BETWEEN :from AND :to
			  AND (:documentType IS NULL OR d.documentType = :documentType)
			  AND (:ownerType IS NULL OR d.ownerType = :ownerType)
			ORDER BY d.expirationDate, d.documentId""",
			countQuery = """
			SELECT COUNT(d) FROM DocumentJpaEntity d
			WHERE d.status IN :statuses
			  AND d.expirationDate IS NOT NULL
			  AND d.expirationDate BETWEEN :from AND :to
			  AND (:documentType IS NULL OR d.documentType = :documentType)
			  AND (:ownerType IS NULL OR d.ownerType = :ownerType)""")
	Page<ExpiringDocumentRow> findExpiringBetween(@Param("statuses") Collection<String> statuses,
			@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("documentType") String documentType, @Param("ownerType") String ownerType, Pageable pageable);

	/**
	 * Listado general de documentos con filtros combinables (HU-DOC-03).
	 * Evalúa los estados activos según la fecha de negocio.
	 */
	@Query("""
		SELECT d FROM DocumentJpaEntity d
		WHERE (:ownerType IS NULL OR d.ownerType = :ownerType)
		  AND (:ownerId IS NULL OR d.ownerId = :ownerId)
		  AND (:documentType IS NULL OR d.documentType = :documentType)
		  AND (:expirationDateFrom IS NULL OR d.expirationDate >= :expirationDateFrom)
		  AND (:expirationDateTo IS NULL OR d.expirationDate <= :expirationDateTo)
		  AND (
		    (:status IS NULL AND d.status <> 'CANCELLED')
		    OR (:status = 'CANCELLED' AND d.status = 'CANCELLED')
		    OR (:status = 'REPLACED' AND d.status = 'REPLACED')
		    OR (
		      d.status IN ('VALID', 'EXPIRING_SOON', 'EXPIRED')
		      AND (
		        (:status = 'EXPIRED' AND d.expirationDate < :today)
		        OR (:status = 'EXPIRING_SOON'
		            AND d.expirationDate BETWEEN :today AND :alertHorizon)
		        OR (:status = 'VALID'
		            AND (d.expirationDate IS NULL OR d.expirationDate > :alertHorizon))
		      )
		    )
		  )
		ORDER BY d.expirationDate ASC NULLS LAST, d.documentId ASC
		""")
	Page<DocumentJpaEntity> findDocuments(
			@Param("ownerType") String ownerType,
			@Param("ownerId") UUID ownerId,
			@Param("documentType") String documentType,
			@Param("status") String status,
			@Param("expirationDateFrom") LocalDate expirationDateFrom,
			@Param("expirationDateTo") LocalDate expirationDateTo,
			@Param("today") LocalDate today,
			@Param("alertHorizon") LocalDate alertHorizon,
			Pageable pageable);

	/**
	 * Fila que devuelve {@link #findExpiringBetween}. Spring llena cada método con la columna del mismo alias en la
	 * consulta (por ejemplo, {@code AS licensePlate} llena {@code getLicensePlate()}).
	 */
	interface ExpiringDocumentRow {

		UUID getDocumentId();

		String getDocumentType();

		String getOwnerType();

		UUID getOwnerId();

		String getDocumentNumber();

		LocalDate getExpirationDate();

		String getLicensePlate();

		String getFullName();

	}

}
