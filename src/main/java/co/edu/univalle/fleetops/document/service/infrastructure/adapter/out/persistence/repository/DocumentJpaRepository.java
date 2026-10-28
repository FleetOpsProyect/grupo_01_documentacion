package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;

public interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {

	boolean existsByDocumentTypeAndDocumentNumberAndStatusIn(String documentType, String documentNumber,
			Collection<String> statuses);

	boolean existsByOwnerTypeAndOwnerIdAndDocumentTypeAndStatusIn(String ownerType, UUID ownerId,
			String documentType, Collection<String> statuses);

}
