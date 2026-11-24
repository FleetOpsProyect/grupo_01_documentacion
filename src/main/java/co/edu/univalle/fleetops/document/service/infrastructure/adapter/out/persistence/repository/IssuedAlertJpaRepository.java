package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.IssuedAlertJpaEntity;

public interface IssuedAlertJpaRepository extends JpaRepository<IssuedAlertJpaEntity, UUID> {

	@Query("SELECT a.thresholdDays FROM IssuedAlertJpaEntity a WHERE a.documentId = :documentId")
	List<Integer> findThresholdDaysByDocumentId(@Param("documentId") UUID documentId);

}
