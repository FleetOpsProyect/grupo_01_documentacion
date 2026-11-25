package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las sentencias de HU-DOC-12 son constantes (Sonar java:S2077): cada tipo de propietario usa la suya, con los
 * valores siempre como parámetros con nombre. La consulta contra PostgreSQL la cubre {@code ValidityCheckIT}.
 */
class EligibleOwnerQueryAdapterTest {

	/**
	 * Una palabra clave pegada a un identificador, por ejemplo {@code is_activeAND}, indica un espacio faltante.
	 */
	private static final Pattern MISSING_SPACE = Pattern.compile("[a-z0-9_)'](AND|OR|FROM|WHERE|ORDER|LIMIT)\\b");

	@Test
	void should_use_the_statements_of_each_owner_type() {
		assertThat(EligibleOwnerQueryAdapter.countSql(OwnerType.VEHICLE))
			.isSameAs(EligibleOwnerQueryAdapter.COUNT_VEHICLES)
			.contains("FROM document.vehicle_replica o");
		assertThat(EligibleOwnerQueryAdapter.pageSql(OwnerType.VEHICLE))
			.isSameAs(EligibleOwnerQueryAdapter.PAGE_VEHICLES)
			.contains("ORDER BY o.license_plate, o.vehicle_id");
		assertThat(EligibleOwnerQueryAdapter.countSql(OwnerType.DRIVER))
			.isSameAs(EligibleOwnerQueryAdapter.COUNT_DRIVERS)
			.contains("FROM document.driver_replica o");
		assertThat(EligibleOwnerQueryAdapter.pageSql(OwnerType.DRIVER))
			.isSameAs(EligibleOwnerQueryAdapter.PAGE_DRIVERS)
			.contains("ORDER BY o.full_name, o.driver_id");
	}

	@Test
	void should_pass_every_value_as_a_named_parameter() {
		for (String sql : List.of(EligibleOwnerQueryAdapter.COUNT_VEHICLES, EligibleOwnerQueryAdapter.PAGE_VEHICLES,
				EligibleOwnerQueryAdapter.COUNT_DRIVERS, EligibleOwnerQueryAdapter.PAGE_DRIVERS)) {
			assertThat(sql).contains(":ownerType", ":validUntil", ":filterByIds", ":ownerIds")
				.doesNotContainPattern(MISSING_SPACE);
		}
		assertThat(EligibleOwnerQueryAdapter.VALID_DOCUMENTS).contains(":ownerType", ":validUntil", ":ownerIds")
			.doesNotContainPattern(MISSING_SPACE);
		assertThat(EligibleOwnerQueryAdapter.PAGE_VEHICLES).contains("LIMIT :limit OFFSET :offset");
	}

}
