package co.edu.univalle.fleetops.document.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-DOC-12 de punta a punta contra PostgreSQL 18 real, con el esquema y el catálogo de {@code database/}: regla de
 * aptitud (criterio 2), forma de la respuesta y paginación (criterio 3), 400 y 422 (criterio 4) y solo lectura
 * (criterio 5). Las consultas filtran por owner_ids para no depender de los datos de otras pruebas. Si no hay Docker,
 * la clase se omite.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ValidityCheckIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.24")
		.withCopyFileToContainer(MountableFile.forHostPath("database/document_schema.sql"),
				"/docker-entrypoint-initdb.d/01_document_schema.sql");

	private static final String PATH = "/documents/validity-check";

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	/**
	 * Fecha consultada: dentro de 60 días.
	 */
	private static final LocalDate VALID_UNTIL = TODAY.plusDays(60);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID compliant;

	private UUID expiringSoonButCompliant;

	private UUID soatExpiresBefore;

	private UUID withoutPropertyCard;

	private UUID inactive;

	private UUID cancelledSoat;

	private final UUID notInReplica = UUID.randomUUID();

	private String vehicleIds;

	@BeforeEach
	void createVehicles() {
		// Apto: SOAT vigente después de la fecha, tecnomecánica que vence justo ese día y tarjeta de propiedad
		this.compliant = vehicle("VCA", true);
		document("SOAT", this.compliant, "VALID", VALID_UNTIL.plusDays(10));
		document("TECHNICAL_INSPECTION", this.compliant, "VALID", VALID_UNTIL);
		document("PROPERTY_CARD", this.compliant, "VALID", null);
		// Apto aunque hoy esté EXPIRING_SOON: lo que cuenta es la fecha de vencimiento
		this.expiringSoonButCompliant = vehicle("VCF", true);
		document("SOAT", this.expiringSoonButCompliant, "EXPIRING_SOON", VALID_UNTIL.plusDays(5));
		document("TECHNICAL_INSPECTION", this.expiringSoonButCompliant, "VALID", VALID_UNTIL.plusDays(300));
		document("PROPERTY_CARD", this.expiringSoonButCompliant, "VALID", null);
		// No aptos
		this.soatExpiresBefore = vehicle("VCB", true);
		document("SOAT", this.soatExpiresBefore, "VALID", VALID_UNTIL.minusDays(1));
		document("TECHNICAL_INSPECTION", this.soatExpiresBefore, "VALID", VALID_UNTIL.plusDays(30));
		document("PROPERTY_CARD", this.soatExpiresBefore, "VALID", null);
		this.withoutPropertyCard = vehicle("VCC", true);
		document("SOAT", this.withoutPropertyCard, "VALID", VALID_UNTIL.plusDays(30));
		document("TECHNICAL_INSPECTION", this.withoutPropertyCard, "VALID", VALID_UNTIL.plusDays(30));
		this.inactive = vehicle("VCD", false);
		document("SOAT", this.inactive, "VALID", VALID_UNTIL.plusDays(30));
		document("TECHNICAL_INSPECTION", this.inactive, "VALID", VALID_UNTIL.plusDays(30));
		document("PROPERTY_CARD", this.inactive, "VALID", null);
		this.cancelledSoat = vehicle("VCE", true);
		document("SOAT", this.cancelledSoat, "CANCELLED", VALID_UNTIL.plusDays(30));
		document("TECHNICAL_INSPECTION", this.cancelledSoat, "VALID", VALID_UNTIL.plusDays(30));
		document("PROPERTY_CARD", this.cancelledSoat, "VALID", null);
		this.vehicleIds = String.join(",", this.compliant.toString(), this.expiringSoonButCompliant.toString(),
				this.soatExpiresBefore.toString(), this.withoutPropertyCard.toString(), this.inactive.toString(),
				this.cancelledSoat.toString(), this.notInReplica.toString());
	}

	private MockHttpServletRequestBuilder validityCheck(String ownerType, String ownerIds) {
		return get(PATH).param("owner_type", ownerType)
			.param("valid_until", VALID_UNTIL.toString())
			.param("owner_ids", ownerIds);
	}

	@Test
	void should_return_only_the_vehicles_with_every_mandatory_document_valid_until_the_date() throws Exception {
		this.mockMvc.perform(validityCheck("VEHICLE", this.vehicleIds))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.total_elements").value(2))
			.andExpect(jsonPath("$.total_pages").value(1))
			.andExpect(jsonPath("$.data", hasSize(2)))
			// Ordenados por placa
			.andExpect(jsonPath("$.data[0].owner_id").value(this.compliant.toString()))
			.andExpect(jsonPath("$.data[0].license_plate").value(plate("VCA", this.compliant)))
			.andExpect(jsonPath("$.data[0].full_name").value(nullValue()))
			.andExpect(jsonPath("$.data[0].documents", hasSize(3)))
			.andExpect(jsonPath("$.data[0].documents[0].document_type").value("SOAT"))
			.andExpect(jsonPath("$.data[0].documents[0].expiration_date").value(VALID_UNTIL.plusDays(10).toString()))
			.andExpect(jsonPath("$.data[0].documents[1].document_type").value("TECHNICAL_INSPECTION"))
			.andExpect(jsonPath("$.data[0].documents[1].expiration_date").value(VALID_UNTIL.toString()))
			.andExpect(jsonPath("$.data[0].documents[2].document_type").value("PROPERTY_CARD"))
			.andExpect(jsonPath("$.data[0].documents[2].expiration_date").value(nullValue()))
			.andExpect(jsonPath("$.data[1].owner_id").value(this.expiringSoonButCompliant.toString()));
	}

	@Test
	void should_paginate_over_the_eligible_owners() throws Exception {
		this.mockMvc.perform(validityCheck("VEHICLE", this.vehicleIds).param("page", "1").param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.total_elements").value(2))
			.andExpect(jsonPath("$.total_pages").value(2))
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.data[0].owner_id").value(this.expiringSoonButCompliant.toString()));

		this.mockMvc.perform(validityCheck("VEHICLE", this.vehicleIds).param("page", "5"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total_elements").value(2))
			.andExpect(jsonPath("$.data", hasSize(0)));
	}

	@Test
	void should_evaluate_every_active_owner_when_owner_ids_is_omitted() throws Exception {
		this.mockMvc
			.perform(get(PATH).param("owner_type", "VEHICLE")
				.param("valid_until", VALID_UNTIL.toString())
				.param("size", "100"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[*].owner_id", hasItem(this.compliant.toString())));
	}

	@Test
	void should_return_the_drivers_with_a_valid_license_and_their_name() throws Exception {
		// Arrange
		UUID valid = driver("Conductora vigente");
		document("DRIVER_LICENSE", valid, "VALID", VALID_UNTIL.plusDays(1));
		UUID expiresBefore = driver("Conductor que vence antes");
		document("DRIVER_LICENSE", expiresBefore, "VALID", VALID_UNTIL.minusDays(1));
		UUID withoutLicense = driver("Conductor sin licencia");

		// Act + Assert
		this.mockMvc.perform(validityCheck("DRIVER", valid + "," + expiresBefore + "," + withoutLicense))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total_elements").value(1))
			.andExpect(jsonPath("$.data[0].owner_id").value(valid.toString()))
			.andExpect(jsonPath("$.data[0].full_name").value("Conductora vigente"))
			.andExpect(jsonPath("$.data[0].license_plate").value(nullValue()))
			.andExpect(jsonPath("$.data[0].documents[0].document_type").value("DRIVER_LICENSE"));
	}

	@Test
	void should_reject_a_date_before_today_with_422() throws Exception {
		this.mockMvc
			.perform(get(PATH).param("owner_type", "VEHICLE").param("valid_until", TODAY.minusDays(1).toString()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail")
				.value("valid_until no puede ser anterior a la fecha actual (" + TODAY + ")."));
	}

	@Test
	void should_reject_an_invalid_parameter_with_400() throws Exception {
		this.mockMvc.perform(get(PATH).param("owner_type", "VEHICLE").param("valid_until", "2026-02-30"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail")
				.value("El parámetro 'valid_until' es obligatorio y debe tener formato YYYY-MM-DD."));
	}

	@Test
	void should_not_modify_data_or_publish_events() throws Exception {
		// Criterio 5
		String before = snapshot();

		this.mockMvc.perform(validityCheck("VEHICLE", this.vehicleIds)).andExpect(status().isOk());

		assertThat(snapshot()).isEqualTo(before);
	}

	private String snapshot() {
		return this.jdbc.queryForObject("""
				SELECT (SELECT count(*) FROM document.outbox_events) || '/'
				    || (SELECT count(*) FROM document.document_audit_log) || '/'
				    || (SELECT coalesce(sum(version), 0) FROM document.documents)""", String.class);
	}

	private static String plate(String prefix, UUID id) {
		return prefix + id.toString().substring(0, 4);
	}

	private UUID vehicle(String platePrefix, boolean active) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO document.vehicle_replica (vehicle_id, license_plate, is_active, last_event_at) "
				+ "VALUES (?, ?, ?, now())", id, plate(platePrefix, id), active);
		return id;
	}

	private UUID driver(String fullName) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO document.driver_replica (driver_id, full_name, is_active, last_event_at) "
				+ "VALUES (?, ?, true, now())", id, fullName);
		return id;
	}

	private void document(String type, UUID owner, String status, LocalDate expirationDate) {
		boolean cancelled = "CANCELLED".equals(status);
		this.jdbc.update("""
				INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity,
				                                issue_date, expiration_date, status, license_category,
				                                cancellation_reason, cancelled_at)
				VALUES (?, ?, ?, ?, 'Entidad de prueba', ?, ?, ?, ?, ?, ?)""", type,
				"DRIVER_LICENSE".equals(type) ? "DRIVER" : "VEHICLE", owner,
				"VC-" + UUID.randomUUID().toString().substring(0, 18), TODAY.minusYears(1), expirationDate, status,
				"DRIVER_LICENSE".equals(type) ? "C1" : null, cancelled ? "Registrado por error en la prueba" : null,
				cancelled ? Timestamp.from(Instant.now()) : null);
	}

}
