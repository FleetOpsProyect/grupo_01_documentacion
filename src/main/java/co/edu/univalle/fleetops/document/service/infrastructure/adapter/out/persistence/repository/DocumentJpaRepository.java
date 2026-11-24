package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

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

}
