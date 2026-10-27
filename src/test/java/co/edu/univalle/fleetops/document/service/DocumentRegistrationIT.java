package co.edu.univalle.fleetops.document.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo transaccional de punta a punta (HU-DOC-01 y HU-DOC-02): REST, caso de uso, dominio y PostgreSQL 18 real,
 * con el esquema creado por los mismos scripts de {@code database/} que usa docker compose. Además valida que las
 * entidades JPA coincidan con el DDL ({@code ddl-auto: validate}).
 *
 * <p>Cada prueba crea sus propios propietarios en la réplica, para no depender del orden ni de la semilla. Si no hay
 * Docker, la clase se omite.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DocumentRegistrationIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.24")
		.withCopyFileToContainer(MountableFile.forHostPath("database/document_schema.sql"),
				"/docker-entrypoint-initdb.d/01_document_schema.sql")
		.withCopyFileToContainer(MountableFile.forHostPath("database/document_seed_dev.sql"),
				"/docker-entrypoint-initdb.d/02_document_seed_dev.sql");

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void should_register_a_soat_with_its_audit_and_a_pending_document_registered_event() throws Exception {
		// Arrange
		UUID vehicle = newVehicle(true);
		String number = uniqueNumber("SOAT");

		// Act
		MvcResult result = this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON)
				.content(vehicleDocument("SOAT", vehicle, number, TODAY.plusYears(1))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("VALID"))
			.andExpect(jsonPath("$.days_remaining")
				.value((int) ChronoUnit.DAYS.between(TODAY, TODAY.plusYears(1))))
			.andReturn();

		// Assert
		UUID documentId = UUID.fromString(result.getResponse().getHeader("Location").replace("/documents/", ""));
		Map<String, Object> row = this.jdbc.queryForMap(
				"SELECT document_type, owner_type, status, version FROM document.documents WHERE document_id = ?",
				documentId);
		assertThat(row).containsEntry("document_type", "SOAT")
			.containsEntry("owner_type", "VEHICLE")
			.containsEntry("status", "VALID")
			.containsEntry("version", 0);

		Map<String, Object> event = this.jdbc.queryForMap("""
				SELECT event_type, topic, message_key, status, payload->>'event_type' AS envelope_type,
				       payload->'data'->>'document_id' AS data_document_id, payload::text AS payload
				FROM document.outbox_events WHERE aggregate_id = ?""", documentId);
		assertThat(event).containsEntry("event_type", "document_registered_event")
			.containsEntry("topic", "fleetops_documents_events")
			.containsEntry("message_key", vehicle.toString())
			.containsEntry("status", "PENDING")
			.containsEntry("envelope_type", "document_registered_event")
			.containsEntry("data_document_id", documentId.toString());
		assertThat((String) event.get("payload")).doesNotContain("document_number").doesNotContain(number);

		String auditedNumber = this.jdbc.queryForObject("""
				SELECT changes->'after'->>'document_number' FROM document.document_audit_log
				WHERE document_id = ? AND action = 'REGISTERED'""", String.class, documentId);
		assertThat(auditedNumber).isEqualTo(number);
	}

	@Test
	void should_register_an_already_expired_technical_inspection_with_both_events() throws Exception {
		// Arrange
		UUID vehicle = newVehicle(true);

		// Act
		MvcResult result = this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON)
				.content(vehicleDocument("TECHNICAL_INSPECTION", vehicle, uniqueNumber("RTM"), TODAY.minusDays(1))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("EXPIRED"))
			.andExpect(jsonPath("$.days_remaining").value(-1))
			.andReturn();

		// Assert
		UUID documentId = UUID.fromString(result.getResponse().getHeader("Location").replace("/documents/", ""));
		assertThat(this.jdbc.queryForList(
				"SELECT event_type FROM document.outbox_events WHERE aggregate_id = ? ORDER BY event_type",
				String.class, documentId))
			.containsExactly("document_expired_event", "document_registered_event");
	}

	@Test
	void should_register_a_property_card_without_expiration() throws Exception {
		// Arrange
		UUID vehicle = newVehicle(true);

		// Act + Assert
		this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON)
				.content(vehicleDocument("PROPERTY_CARD", vehicle, uniqueNumber("TP"), null)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("VALID"))
			.andExpect(jsonPath("$.expiration_date").value(nullValue()))
			.andExpect(jsonPath("$.days_remaining").value(nullValue()));
	}

	@Test
	void should_register_a_driver_license_with_its_single_category() throws Exception {
		// Arrange
		UUID driver = newDriver();

		// Act + Assert
		this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON).content("""
					{ "document_type": "DRIVER_LICENSE", "owner_type": "DRIVER", "owner_id": "%s",
					  "document_number": "%s", "issuing_entity": "Secretaría de Movilidad de Cali",
					  "issue_date": "%s", "expiration_date": "%s", "license_category": "C1" }
					""".formatted(driver, uniqueNumber("LIC"), TODAY.minusYears(1), TODAY.plusYears(9))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.owner_type").value("DRIVER"))
			.andExpect(jsonPath("$.license_category").value("C1"));
	}

	@Test
	void should_reject_with_409_a_number_already_registered_for_the_type() throws Exception {
		// Arrange
		String number = uniqueNumber("SOAT");
		register(vehicleDocument("SOAT", newVehicle(true), number, TODAY.plusYears(1)));

		// Act + Assert
		this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON)
				.content(vehicleDocument("SOAT", newVehicle(true), number, TODAY.plusYears(1))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Ya existe un documento SOAT con ese document_number"));
	}

	@Test
	void should_reject_with_409_a_second_active_soat_for_the_same_vehicle() throws Exception {
		// Arrange
		UUID vehicle = newVehicle(true);
		register(vehicleDocument("SOAT", vehicle, uniqueNumber("SOAT"), TODAY.plusYears(1)));

		// Act + Assert
		this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON)
				.content(vehicleDocument("SOAT", vehicle, uniqueNumber("SOAT"), TODAY.plusYears(1))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail")
				.value("El propietario ya tiene un documento SOAT activo. Para cambiarlo use la renovación"));
	}

	@Test
	void should_reject_with_422_a_vehicle_that_is_not_active_in_the_replica() throws Exception {
		this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON)
				.content(vehicleDocument("SOAT", newVehicle(false), uniqueNumber("SOAT"), TODAY.plusYears(1))))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void should_reject_with_422_a_soat_without_expiration_date() throws Exception {
		this.mockMvc
			.perform(post("/documents").contentType(MediaType.APPLICATION_JSON)
				.content(vehicleDocument("SOAT", newVehicle(true), uniqueNumber("SOAT"), null)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail").value("expiration_date es obligatoria para el tipo SOAT"));
	}

	@Test
	void should_load_the_development_seed_with_the_replicas() {
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM document.vehicle_replica WHERE vehicle_id = '01960000-0000-7000-8000-000000000001'",
				Integer.class))
			.isEqualTo(1);
	}

	@Test
	void should_report_health_up_with_the_database_available() throws Exception {
		this.mockMvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}

	private void register(String body) throws Exception {
		this.mockMvc.perform(post("/documents").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
	}

	private UUID newVehicle(boolean active) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO document.vehicle_replica (vehicle_id, license_plate, is_active, last_event_at) "
				+ "VALUES (?, ?, ?, now())", id, "IT" + id.toString().substring(0, 4), active);
		return id;
	}

	private UUID newDriver() {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO document.driver_replica (driver_id, full_name, is_active, last_event_at) "
				+ "VALUES (?, 'Conductor de prueba', true, now())", id);
		return id;
	}

	private static String uniqueNumber(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 18);
	}

	private static String vehicleDocument(String type, UUID vehicle, String number, LocalDate expirationDate) {
		String expiration = (expirationDate != null) ? "\"" + expirationDate + "\"" : "null";
		return """
				{ "document_type": "%s", "owner_type": "VEHICLE", "owner_id": "%s",
				  "document_number": "%s", "issuing_entity": "Entidad de prueba",
				  "issue_date": "%s", "expiration_date": %s }
				""".formatted(type, vehicle, number, TODAY.minusYears(1), expiration);
	}

}
