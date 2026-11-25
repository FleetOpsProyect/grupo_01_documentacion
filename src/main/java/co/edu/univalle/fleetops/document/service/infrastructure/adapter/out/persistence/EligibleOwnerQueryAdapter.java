package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import co.edu.univalle.fleetops.document.service.application.port.out.EligibleOwnerRepository;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

/**
 * Adaptador de {@link EligibleOwnerRepository}: consulta de solo lectura en SQL sobre la réplica, los documentos y el
 * catálogo {@code document_types} (HU-DOC-12).
 *
 * <p>La regla de aptitud (DAS sec. 6.3) se expresa como «no existe un tipo obligatorio y activo del catálogo para el
 * que falte un documento vigente», así que los tipos obligatorios salen del catálogo y no del código (ADR-DOC-10). Un
 * documento vigente es uno activo con expiration_date igual o posterior a valid_until; en los tipos sin vencimiento,
 * uno en VALID.
 *
 * <p>Seguridad (Sonar java:S2077): todas las sentencias son constantes de compilación, una por tipo de propietario,
 * y todos los valores viajan como parámetros con nombre. Ninguna parte del SQL se arma en tiempo de ejecución; el
 * filtro opcional de owner_ids también es un parámetro ({@code :filterByIds}).
 */
@Component
public class EligibleOwnerQueryAdapter implements EligibleOwnerRepository {

	/**
	 * Documento vigente hasta valid_until para su tipo del catálogo (alias {@code d} y {@code t}).
	 */
	private static final String VALID_DOCUMENT = " d.status IN ('VALID', 'EXPIRING_SOON', 'EXPIRED')"
			+ " AND ((t.has_expiration AND d.expiration_date >= :validUntil)"
			+ " OR (NOT t.has_expiration AND d.status = 'VALID'))";

	/**
	 * Tipo obligatorio y activo del catálogo para el tipo de propietario (alias {@code t}).
	 */
	private static final String MANDATORY_TYPE = " t.owner_type = :ownerType AND t.is_mandatory AND t.is_active";

	/**
	 * Ningún tipo obligatorio sin un documento vigente del propietario {@code o.<id>}: se completa con la columna de
	 * id de la réplica.
	 */
	private static final String NO_MISSING_MANDATORY_DOCUMENT = " AND NOT EXISTS (SELECT 1"
			+ " FROM document.document_types t WHERE" + MANDATORY_TYPE
			+ " AND NOT EXISTS (SELECT 1 FROM document.documents d"
			+ " WHERE d.owner_type = :ownerType AND d.document_type = t.code AND" + VALID_DOCUMENT
			+ " AND d.owner_id = ";

	private static final String VEHICLES_ELIGIBLE = " FROM document.vehicle_replica o WHERE o.is_active"
			+ " AND (:filterByIds = FALSE OR o.vehicle_id IN (:ownerIds))"
			+ NO_MISSING_MANDATORY_DOCUMENT + "o.vehicle_id))";

	private static final String DRIVERS_ELIGIBLE = " FROM document.driver_replica o WHERE o.is_active"
			+ " AND (:filterByIds = FALSE OR o.driver_id IN (:ownerIds))"
			+ NO_MISSING_MANDATORY_DOCUMENT + "o.driver_id))";

	static final String COUNT_VEHICLES = "SELECT count(*)" + VEHICLES_ELIGIBLE;

	static final String PAGE_VEHICLES = "SELECT o.vehicle_id AS owner_id, o.license_plate AS label"
			+ VEHICLES_ELIGIBLE + " ORDER BY o.license_plate, o.vehicle_id LIMIT :limit OFFSET :offset";

	static final String COUNT_DRIVERS = "SELECT count(*)" + DRIVERS_ELIGIBLE;

	static final String PAGE_DRIVERS = "SELECT o.driver_id AS owner_id, o.full_name AS label"
			+ DRIVERS_ELIGIBLE + " ORDER BY o.full_name, o.driver_id LIMIT :limit OFFSET :offset";

	static final String VALID_DOCUMENTS = "SELECT d.owner_id, d.document_type, d.expiration_date"
			+ " FROM document.documents d JOIN document.document_types t ON t.code = d.document_type"
			+ " WHERE d.owner_type = :ownerType AND d.owner_id IN (:ownerIds) AND" + MANDATORY_TYPE + " AND"
			+ VALID_DOCUMENT;

	/**
	 * Valor de relleno para {@code IN (:ownerIds)} cuando no se filtra: SQL no admite una lista vacía. Con
	 * {@code :filterByIds = FALSE} la condición no lo usa.
	 */
	static final UUID NO_OWNER = new UUID(0, 0);

	private static final String OWNER_IDS = "ownerIds";

	private final NamedParameterJdbcTemplate jdbc;

	public EligibleOwnerQueryAdapter(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public EligibleOwners findEligible(OwnerType ownerType, LocalDate validUntil, Set<UUID> ownerIds, long offset,
			int limit) {
		boolean filterByIds = !ownerIds.isEmpty();
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("ownerType", ownerType.name())
			.addValue("validUntil", validUntil)
			.addValue("filterByIds", filterByIds)
			.addValue(OWNER_IDS, filterByIds ? ownerIds : Set.of(NO_OWNER))
			.addValue("offset", offset)
			.addValue("limit", limit);
		Long count = this.jdbc.queryForObject(countSql(ownerType), parameters, Long.class);
		long total = (count != null) ? count : 0;
		if (offset >= total) {
			return new EligibleOwners(total, List.of());
		}
		Map<UUID, String> labels = new LinkedHashMap<>();
		this.jdbc.query(pageSql(ownerType), parameters,
				row -> {
					labels.put(row.getObject("owner_id", UUID.class), row.getString("label"));
				});
		Map<UUID, List<ValidDocument>> documents = validDocuments(labels.keySet(), parameters);
		List<EligibleOwner> page = new ArrayList<>();
		labels.forEach((ownerId, label) -> page
			.add(new EligibleOwner(ownerId, label, documents.getOrDefault(ownerId, List.of()))));
		return new EligibleOwners(total, page);
	}

	static String countSql(OwnerType ownerType) {
		return switch (ownerType) {
			case VEHICLE -> COUNT_VEHICLES;
			case DRIVER -> COUNT_DRIVERS;
		};
	}

	static String pageSql(OwnerType ownerType) {
		return switch (ownerType) {
			case VEHICLE -> PAGE_VEHICLES;
			case DRIVER -> PAGE_DRIVERS;
		};
	}

	private Map<UUID, List<ValidDocument>> validDocuments(Set<UUID> pageOwnerIds, MapSqlParameterSource parameters) {
		parameters.addValue(OWNER_IDS, pageOwnerIds);
		Map<UUID, List<ValidDocument>> documents = new LinkedHashMap<>();
		this.jdbc.query(VALID_DOCUMENTS, parameters, row -> {
			documents.computeIfAbsent(row.getObject("owner_id", UUID.class), id -> new ArrayList<>())
				.add(new ValidDocument(DocumentTypeCode.valueOf(row.getString("document_type")),
						row.getObject("expiration_date", LocalDate.class)));
		});
		return documents;
	}

}
