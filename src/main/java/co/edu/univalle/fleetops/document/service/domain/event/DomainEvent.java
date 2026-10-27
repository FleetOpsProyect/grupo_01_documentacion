package co.edu.univalle.fleetops.document.service.domain.event;

import java.time.Instant;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;

/**
 * Hecho de negocio que el núcleo registra en el outbox (ADR-DOC-05). Es sellado para que el adaptador que arma el
 * sobre y el {@code event_type} cubra todos los eventos sin un caso por defecto (DAS sec. 8.1, ADR-DOC-14).
 */
public sealed interface DomainEvent permits DocumentRegistered, DocumentExpired {

	UUID documentId();

	OwnerRef owner();

	Instant occurredAt();

}
