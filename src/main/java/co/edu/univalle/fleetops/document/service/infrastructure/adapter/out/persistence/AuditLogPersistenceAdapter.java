package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import co.edu.univalle.fleetops.document.service.application.port.out.AuditLogRepository;
import co.edu.univalle.fleetops.document.service.application.port.out.IdGenerator;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentAuditLogJpaEntity;

/**
 * Adaptador de {@link AuditLogRepository}: inserta en {@code document_audit_log} dentro de la transacción en curso.
 * {@code changes} guarda el antes y el después; en un registro solo hay después. Es de uso interno y guarda el
 * document_number completo (DAS sec. 6.4).
 */
@Component
public class AuditLogPersistenceAdapter implements AuditLogRepository {

	static final String ACTION_REGISTERED = "REGISTERED";

	static final String ACTION_STATUS_CHANGED = "STATUS_CHANGED";

	static final String ACTION_CANCELLED = "CANCELLED";

	private static final String STATUS = "status";

	private final EntityManager entityManager;

	private final JsonMapper jsonMapper;

	private final IdGenerator idGenerator;

	public AuditLogPersistenceAdapter(EntityManager entityManager, JsonMapper jsonMapper, IdGenerator idGenerator) {
		this.entityManager = entityManager;
		this.jsonMapper = jsonMapper;
		this.idGenerator = idGenerator;
	}

	@Override
	public void recordRegistration(Document document, UUID correlationId) {
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("before", null);
		changes.put("after", snapshot(document));
		this.entityManager.persist(new DocumentAuditLogJpaEntity(this.idGenerator.newId(), document.id(),
				ACTION_REGISTERED, this.jsonMapper.writeValueAsString(changes), null, correlationId,
				document.createdAt()));
	}

	@Override
	public void recordStatusChange(UUID documentId, DocumentStatus previous, DocumentStatus current,
			UUID correlationId, Instant changedAt) {
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("before", Map.of(STATUS, previous.name()));
		changes.put("after", Map.of(STATUS, current.name()));
		this.entityManager.persist(new DocumentAuditLogJpaEntity(this.idGenerator.newId(), documentId,
				ACTION_STATUS_CHANGED, this.jsonMapper.writeValueAsString(changes), null, correlationId, changedAt));
	}

	@Override
	public void recordCancellation(Document previous, Document cancelled,
								   UUID correlationId) {

		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("before", cancellationSnapshot(previous));
		changes.put("after", cancellationSnapshot(cancelled));

		this.entityManager.persist(new DocumentAuditLogJpaEntity(
				this.idGenerator.newId(),
				cancelled.id(),
				ACTION_CANCELLED,
				this.jsonMapper.writeValueAsString(changes),
				cancelled.cancellationReason(),
				correlationId,
				cancelled.cancelledAt()));
	}

	private static Map<String, Object> snapshot(Document document) {
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("document_type", document.documentType().name());
		after.put("owner_type", document.owner().ownerType().name());
		after.put("owner_id", document.owner().ownerId().toString());
		after.put("document_number", document.documentNumber().value());
		after.put("issuing_entity", document.issuingEntity());
		after.put("issue_date", document.validity().issueDate().toString());
		after.put("expiration_date",
				(document.validity().expirationDate() != null) ? document.validity().expirationDate().toString()
						: null);
		after.put("license_category",
				(document.licenseCategory() != null) ? document.licenseCategory().value() : null);
		after.put("status", document.status().name());
		return after;
	}

	/**
	 * Construye una representación del documento para la auditoría de anulación.
	 * @param document documento que se desea representar
	 * @return datos del documento y de su anulación
	 */
	private static Map<String, Object> cancellationSnapshot(Document document) {
		Map<String, Object> data = snapshot(document);

		data.put("cancellation_reason", document.cancellationReason());
		data.put("cancelled_at",
				(document.cancelledAt() != null) ? document.cancelledAt().toString() : null);
		data.put("updated_at", document.updatedAt().toString());

		return data;
	}

}
