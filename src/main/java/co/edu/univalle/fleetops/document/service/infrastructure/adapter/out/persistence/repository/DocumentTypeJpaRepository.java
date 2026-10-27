package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentTypeJpaEntity;

public interface DocumentTypeJpaRepository extends JpaRepository<DocumentTypeJpaEntity, String> {

}
