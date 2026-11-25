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
 * uno en VALID. Los nombres de tabla y columna salen de constantes, nunca de la entrada del usuario.
 */
@Component
public class EligibleOwnerQueryAdapter implements EligibleOwnerRepository {

	private static final Map<OwnerType, OwnerTable> OWNER_TABLES = Map.of(
			OwnerType.VEHICLE, new OwnerTable("document.vehicle_replica", "vehicle_id", "license_plate"),
			OwnerType.DRIVER, new OwnerTable("document.driver_replica", "driver_id", "full_name"));

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

	private static final String OWNER_IDS = "ownerIds";

	private final NamedParameterJdbcTemplate jdbc;

	public EligibleOwnerQueryAdapter(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public EligibleOwners findEligible(OwnerType ownerType, LocalDate validUntil, Set<UUID> ownerIds, long offset,
			int limit) {
		OwnerTable owners = OWNER_TABLES.get(ownerType);
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("ownerType", ownerType.name())
			.addValue("validUntil", validUntil)
			.addValue(OWNER_IDS, ownerIds)
			.addValue("offset", offset)
			.addValue("limit", limit);
		String eligible = eligibleOwnersSql(owners, !ownerIds.isEmpty());
		Long count = this.jdbc.queryForObject("SELECT count(*)" + eligible, parameters, Long.class);
		long total = (count != null) ? count : 0;
		if (offset >= total) {
			return new EligibleOwners(total, List.of());
		}
		Map<UUID, String> labels = new LinkedHashMap<>();
		this.jdbc.query("SELECT o." + owners.idColumn() + " AS owner_id, o." + owners.labelColumn() + " AS label"
				+ eligible + " ORDER BY o." + owners.labelColumn() + ", o." + owners.idColumn()
				+ " LIMIT :limit OFFSET :offset", parameters,
				row -> {
					labels.put(row.getObject("owner_id", UUID.class), row.getString("label"));
				});
		Map<UUID, List<ValidDocument>> documents = validDocuments(labels.keySet(), parameters);
		List<EligibleOwner> page = new ArrayList<>();
		labels.forEach((ownerId, label) -> page
			.add(new EligibleOwner(ownerId, label, documents.getOrDefault(ownerId, List.of()))));
		return new EligibleOwners(total, page);
	}

	/**
	 * FROM y WHERE de los propietarios aptos (alias {@code o}): activos y sin tipo obligatorio pendiente.
	 */
	static String eligibleOwnersSql(OwnerTable owners, boolean filterByIds) {
		String ownerId = "o." + owners.idColumn();
		return " FROM " + owners.table() + " o"
				+ " WHERE o.is_active"
				+ (filterByIds ? " AND " + ownerId + " IN (:ownerIds)" : "")
				+ " AND NOT EXISTS (SELECT 1 FROM document.document_types t WHERE" + MANDATORY_TYPE
				+ " AND NOT EXISTS (SELECT 1 FROM document.documents d"
				+ " WHERE d.owner_type = :ownerType AND d.owner_id = " + ownerId
				+ " AND d.document_type = t.code AND" + VALID_DOCUMENT + "))";
	}

	private Map<UUID, List<ValidDocument>> validDocuments(Set<UUID> pageOwnerIds, MapSqlParameterSource parameters) {
		parameters.addValue(OWNER_IDS, pageOwnerIds);
		Map<UUID, List<ValidDocument>> documents = new LinkedHashMap<>();
		this.jdbc.query("SELECT d.owner_id, d.document_type, d.expiration_date"
				+ " FROM document.documents d JOIN document.document_types t ON t.code = d.document_type"
				+ " WHERE d.owner_type = :ownerType AND d.owner_id IN (:ownerIds) AND" + MANDATORY_TYPE + " AND"
				+ VALID_DOCUMENT, parameters, row -> {
					documents.computeIfAbsent(row.getObject("owner_id", UUID.class), id -> new ArrayList<>())
						.add(new ValidDocument(DocumentTypeCode.valueOf(row.getString("document_type")),
								row.getObject("expiration_date", LocalDate.class)));
				});
		return documents;
	}

	/**
	 * Tabla de la réplica y sus columnas de id y etiqueta (placa o nombre).
	 */
	record OwnerTable(String table, String idColumn, String labelColumn) {
	}

}
