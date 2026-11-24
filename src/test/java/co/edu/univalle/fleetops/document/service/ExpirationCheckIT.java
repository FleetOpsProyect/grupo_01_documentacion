package co.edu.univalle.fleetops.document.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import co.edu.univalle.fleetops.document.service.application.port.in.CheckExpirationsUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpirationCheckResult;
import co.edu.univalle.fleetops.document.service.domain.event.DocumentExpiringSoon;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-DOC-06 de punta a punta contra PostgreSQL 18 real, con el esquema de {@code database/}: candidatos, estado,
 * auditoría, outbox, issued_alerts y scheduled_job_runs. El job programado está apagado en el perfil de pruebas; aquí
 * se invoca el caso de uso directamente. Si no hay Docker, la clase se omite.
 *
 * <p>Cada prueba borra la ejecución del día para poder volver a verificar la misma fecha, y crea sus propios
 * documentos y propietarios.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true" })
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ExpirationCheckIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.24")
		.withCopyFileToContainer(MountableFile.forHostPath("database/document_schema.sql"),
				"/docker-entrypoint-initdb.d/01_document_schema.sql");

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	private static final String EXPIRING_SOON_EVENT = "document_expiring_soon_event";

	@Autowired
	private CheckExpirationsUseCase useCase;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void allowCheckingTodayAgain() {
		this.jdbc.update("DELETE FROM document.scheduled_job_runs");
	}

	@Test
	void should_mark_expiring_soon_and_publish_the_first_alert_once_per_day() {
		// Arrange: SOAT VALID que vence en 30 días (umbral mayor)
		UUID document = newSoat(newVehicle(true), "VALID", TODAY.plusDays(30));

		// Act
		ExpirationCheckResult first = this.useCase.checkExpirations();
		ExpirationCheckResult second = this.useCase.checkExpirations();

		// Assert: estado y versión (criterio 6)
		assertThat(first.executed()).isTrue();
		assertThat(this.jdbc.queryForMap("SELECT status, version FROM document.documents WHERE document_id = ?",
				document))
			.containsEntry("status", "EXPIRING_SOON")
			.containsEntry("version", 1);
		assertThat(this.jdbc.queryForObject("""
				SELECT changes::text FROM document.document_audit_log
				WHERE document_id = ? AND action = 'STATUS_CHANGED' AND correlation_id = ?""", String.class, document,
				first.runId()))
			.contains("\"before\": {\"status\": \"VALID\"}")
			.contains("\"after\": {\"status\": \"EXPIRING_SOON\"}");

		// Evento con event_id determinista y payload de la hoja «Eventos SNS» (criterio 2)
		Map<String, Object> event = this.jdbc.queryForMap("""
				SELECT event_id, status, payload->>'event_type' AS event_type, payload->>'occurred_at' AS occurred_at,
				       payload->'data'->>'threshold_days' AS threshold, payload->'data'->>'days_remaining' AS days,
				       payload->'data'->>'expiration_date' AS expiration
				FROM document.outbox_events WHERE aggregate_id = ? AND event_type = ?""", document,
				EXPIRING_SOON_EVENT);
		assertThat(event).containsEntry("event_id", DocumentExpiringSoon.eventIdFor(document, 30))
			.containsEntry("status", "PENDING")
			.containsEntry("event_type", EXPIRING_SOON_EVENT)
			.containsEntry("threshold", "30")
			.containsEntry("days", "30")
			.containsEntry("expiration", TODAY.plusDays(30).toString());
		assertThat((String) event.get("occurred_at")).endsWith("-05:00");
		assertThat(issuedAlerts(document)).containsExactly(Map.of("threshold_days", 30, "published", true));

		// Segunda pasada el mismo día: 0 eventos nuevos (criterio 4)
		assertThat(second.executed()).isFalse();
		assertThat(outboxCount(document)).isEqualTo(1);

		// Resumen en scheduled_job_runs (criterio 7)
		assertThat(this.jdbc.queryForMap("""
				SELECT status, evaluated_count >= 1 AS evaluated, issued_count >= 1 AS issued, failed_count,
				       finished_at IS NOT NULL AS finished
				FROM document.scheduled_job_runs WHERE id = ?""", first.runId()))
			.containsEntry("status", "COMPLETED")
			.containsEntry("evaluated", true)
			.containsEntry("issued", true)
			.containsEntry("failed_count", 0)
			.containsEntry("finished", true);
	}

	@Test
	void should_publish_only_the_smallest_pending_threshold_after_an_outage() {
		// Arrange (criterio 5, ADR-DOC-03): el job no corrió y la licencia ya está a 5 días
		UUID document = newLicense(newDriver(), "VALID", TODAY.plusDays(5));

		// Act
		this.useCase.checkExpirations();

		// Assert
		assertThat(outboxCount(document)).isEqualTo(1);
		assertThat(this.jdbc.queryForObject("SELECT event_id FROM document.outbox_events WHERE aggregate_id = ?",
				UUID.class, document))
			.isEqualTo(DocumentExpiringSoon.eventIdFor(document, 7));
		assertThat(issuedAlerts(document)).containsExactly(Map.of("threshold_days", 7, "published", true),
				Map.of("threshold_days", 15, "published", false), Map.of("threshold_days", 30, "published", false));
	}

	@Test
	void should_publish_the_next_threshold_on_a_later_day() {
		// Arrange: el umbral de 30 ya se avisó y hoy el documento está a 15 días
		UUID document = newSoat(newVehicle(true), "EXPIRING_SOON", TODAY.plusDays(15));
		this.jdbc.update("INSERT INTO document.issued_alerts (document_id, threshold_days, published) "
				+ "VALUES (?, 30, false)", document);

		// Act
		this.useCase.checkExpirations();

		// Assert
		assertThat(issuedAlerts(document)).containsExactly(Map.of("threshold_days", 15, "published", true),
				Map.of("threshold_days", 30, "published", false));
		assertThat(this.jdbc.queryForObject("SELECT version FROM document.documents WHERE document_id = ?",
				Integer.class, document))
			.as("el estado ya era EXPIRING_SOON: no se reescribe")
			.isZero();
	}

	@Test
	void should_ignore_documents_that_are_not_candidates() {
		// Arrange
		UUID farAway = newSoat(newVehicle(true), "VALID", TODAY.plusDays(31));
		UUID expired = newSoat(newVehicle(true), "VALID", TODAY.minusDays(1));
		UUID cancelled = newSoat(newVehicle(true), "CANCELLED", TODAY.plusDays(10));
		UUID inactiveOwner = newSoat(newVehicle(false), "VALID", TODAY.plusDays(10));
		UUID propertyCard = newPropertyCard(newVehicle(true));

		// Act
		this.useCase.checkExpirations();

		// Assert: sin eventos, sin umbrales y sin cambio de estado
		for (UUID document : List.of(farAway, expired, cancelled, inactiveOwner, propertyCard)) {
			assertThat(outboxCount(document)).as("eventos de %s", document).isZero();
			assertThat(issuedAlerts(document)).as("umbrales de %s", document).isEmpty();
			assertThat(this.jdbc.queryForObject("SELECT version FROM document.documents WHERE document_id = ?",
					Integer.class, document))
				.as("versión de %s", document)
				.isZero();
		}
	}

	@Test
	void should_take_over_a_failed_execution_of_the_same_day() {
		// Arrange: una ejecución de hoy que falló
		this.jdbc.update("""
				INSERT INTO document.scheduled_job_runs (job_name, business_date, status, instance_id, finished_at)
				VALUES ('document_expiration_check', ?, 'FAILED', 'otra-instancia/1', now())""", TODAY);
		UUID document = newSoat(newVehicle(true), "VALID", TODAY.plusDays(7));

		// Act
		ExpirationCheckResult result = this.useCase.checkExpirations();

		// Assert
		assertThat(result.executed()).isTrue();
		assertThat(outboxCount(document)).isEqualTo(1);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM document.scheduled_job_runs WHERE business_date = ?",
				Integer.class, TODAY))
			.isOne();
		assertThat(this.jdbc.queryForObject("SELECT status FROM document.scheduled_job_runs WHERE business_date = ?",
				String.class, TODAY))
			.isEqualTo("COMPLETED");
	}

	/**
	 * Criterio 1: menos de 5 minutos con 10.000 documentos. Es el peor caso (todos dentro del umbral mayor). Tarda y
	 * llena la base, así que solo corre con FLEETOPS_PERFORMANCE_TESTS=true.
	 */
	@Test
	@EnabledIfEnvironmentVariable(named = "FLEETOPS_PERFORMANCE_TESTS", matches = "true")
	void should_check_ten_thousand_documents_in_less_than_five_minutes() {
		// Arrange: 10.000 SOAT VALID que vencen entre hoy y 30 días, cada uno con su vehículo
		this.jdbc.update("""
				WITH owners AS (
				    INSERT INTO document.vehicle_replica (vehicle_id, license_plate, is_active, last_event_at)
				    SELECT uuidv7(), 'P' || n, true, now() FROM generate_series(1, 10000) AS n
				    RETURNING vehicle_id)
				INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity,
				                                issue_date, expiration_date, status)
				SELECT 'SOAT', 'VEHICLE', vehicle_id, 'PERF-' || row_number() OVER (), 'Aseguradora de prueba',
				       ?::date - 300, ?::date + (row_number() OVER () % 31)::int, 'VALID'
				FROM owners""", TODAY, TODAY);

		// Act
		long start = System.nanoTime();
		ExpirationCheckResult result = this.useCase.checkExpirations();
		Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

		// Assert
		assertThat(result.failed()).isZero();
		assertThat(result.evaluated()).isGreaterThanOrEqualTo(10_000);
		assertThat(elapsed).isLessThan(Duration.ofMinutes(5));
	}

	private List<Map<String, Object>> issuedAlerts(UUID document) {
		return this.jdbc.queryForList("""
				SELECT threshold_days, published FROM document.issued_alerts
				WHERE document_id = ? ORDER BY threshold_days""", document);
	}

	private int outboxCount(UUID document) {
		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM document.outbox_events WHERE aggregate_id = ? AND event_type = ?",
				Integer.class, document, EXPIRING_SOON_EVENT);
		return (count != null) ? count : 0;
	}

	private UUID newVehicle(boolean active) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO document.vehicle_replica (vehicle_id, license_plate, is_active, last_event_at) "
				+ "VALUES (?, ?, ?, now())", id, "EX" + id.toString().substring(0, 4), active);
		return id;
	}

	private UUID newDriver() {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO document.driver_replica (driver_id, full_name, is_active, last_event_at) "
				+ "VALUES (?, 'Conductor de prueba', true, now())", id);
		return id;
	}

	private UUID newSoat(UUID vehicle, String status, LocalDate expirationDate) {
		return insertDocument("SOAT", "VEHICLE", vehicle, status, expirationDate, null);
	}

	private UUID newLicense(UUID driver, String status, LocalDate expirationDate) {
		return insertDocument("DRIVER_LICENSE", "DRIVER", driver, status, expirationDate, "C1");
	}

	private UUID newPropertyCard(UUID vehicle) {
		return insertDocument("PROPERTY_CARD", "VEHICLE", vehicle, "VALID", null, null);
	}

	private UUID insertDocument(String type, String ownerType, UUID owner, String status, LocalDate expirationDate,
			String licenseCategory) {
		UUID id = UUID.randomUUID();
		boolean cancelled = "CANCELLED".equals(status);
		this.jdbc.update("""
				INSERT INTO document.documents (document_id, document_type, owner_type, owner_id, document_number,
				                                issuing_entity, issue_date, expiration_date, status, license_category,
				                                cancellation_reason, cancelled_at)
				VALUES (?, ?, ?, ?, ?, 'Entidad de prueba', ?, ?, ?, ?, ?, ?)""", id, type, ownerType, owner,
				"EXP-" + id.toString().substring(0, 18), TODAY.minusYears(1), expirationDate, status, licenseCategory,
				cancelled ? "Registrado por error en la prueba" : null, cancelled ? java.sql.Timestamp.from(
						java.time.Instant.now()) : null);
		return id;
	}

}
