package co.edu.univalle.fleetops.document.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonUseCase;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonView;
import co.edu.univalle.fleetops.document.service.application.port.in.ExpiringSoonView.ExpiringDocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.PageQuery;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba HU-DOC-08 contra un PostgreSQL real en Docker, para comprobar que la consulta funciona de verdad. Si Docker
 * no está corriendo, estas pruebas se saltan.
 *
 * <p>La base de datos no permite borrar documentos, así que cada prueba crea sus propios vehículos y conductores y
 * solo revisa los documentos que ella misma creó.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true" })
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ExpiringSoonIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.24")
		.withCopyFileToContainer(MountableFile.forHostPath("database/document_schema.sql"),
				"/docker-entrypoint-initdb.d/01_document_schema.sql");

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	private static final PageQuery ALL = new PageQuery(0, 100);

	@Autowired
	private ExpiringSoonUseCase useCase;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void should_return_active_documents_within_the_horizon_with_the_owner_data_ordered_by_expiration() {
		// Arrange
		UUID vehicle = newVehicle("ITV001");
		UUID driver = newDriver("Conductora de prueba");
		UUID inTwentyDays = insertDocument("SOAT", "VEHICLE", vehicle, "VALID", TODAY.plusDays(20), null);
		UUID dueToday = insertDocument("DRIVER_LICENSE", "DRIVER", driver, "EXPIRING_SOON", TODAY, "C1");
		UUID onTheHorizon = insertDocument("TECHNICAL_INSPECTION", "VEHICLE", vehicle, "VALID", TODAY.plusDays(30),
				null);
		UUID ownerNotInReplica = insertDocument("SOAT", "VEHICLE", UUID.randomUUID(), "VALID", TODAY.plusDays(5),
				null);

		// Act
		List<ExpiringDocumentView> found = mine(query(30, null, null),
				Set.of(inTwentyDays, dueToday, onTheHorizon, ownerNotInReplica));

		// Assert: orden por expiration_date (criterio 2) y estado recalculado aunque el guardado sea VALID
		assertThat(found).extracting(ExpiringDocumentView::documentId)
			.containsExactly(dueToday, ownerNotInReplica, inTwentyDays, onTheHorizon);
		assertThat(found).extracting(ExpiringDocumentView::status).containsOnly(DocumentStatus.EXPIRING_SOON);
		assertThat(found).extracting(ExpiringDocumentView::daysRemaining).containsExactly(0, 5, 20, 30);

		// Assert: placa o nombre de la réplica; null si no aplica o si la réplica no lo tiene (criterio 3)
		ExpiringDocumentView license = found.get(0);
		assertThat(license.fullName()).isEqualTo("Conductora de prueba");
		assertThat(license.licensePlate()).isNull();
		assertThat(license.ownerType()).isEqualTo(OwnerType.DRIVER);
		ExpiringDocumentView soat = found.get(2);
		assertThat(soat.licensePlate()).isEqualTo("ITV001");
		assertThat(soat.fullName()).isNull();
		assertThat(soat.documentNumber()).startsWith("EXS-");
		assertThat(found.get(1).licensePlate()).isNull();
	}

	@Test
	void should_leave_out_expired_far_away_cancelled_replaced_documents_and_property_cards() {
		// Arrange
		UUID expired = insertDocument("SOAT", "VEHICLE", newVehicle("ITV002"), "VALID", TODAY.minusDays(1), null);
		UUID farAway = insertDocument("SOAT", "VEHICLE", newVehicle("ITV003"), "VALID", TODAY.plusDays(31), null);
		UUID cancelled = insertDocument("SOAT", "VEHICLE", newVehicle("ITV004"), "CANCELLED", TODAY.plusDays(3),
				null);
		UUID replaced = insertDocument("SOAT", "VEHICLE", newVehicle("ITV005"), "REPLACED", TODAY.plusDays(3), null);
		UUID propertyCard = insertDocument("PROPERTY_CARD", "VEHICLE", newVehicle("ITV006"), "VALID", null, null);

		// Act
		List<ExpiringDocumentView> found = mine(query(30, null, null),
				Set.of(expired, farAway, cancelled, replaced, propertyCard));

		// Assert (criterio 2)
		assertThat(found).isEmpty();
	}

	@Test
	void should_combine_the_document_type_and_owner_type_filters() {
		// Arrange
		UUID soat = insertDocument("SOAT", "VEHICLE", newVehicle("ITV007"), "VALID", TODAY.plusDays(8), null);
		UUID inspection = insertDocument("TECHNICAL_INSPECTION", "VEHICLE", newVehicle("ITV008"), "VALID",
				TODAY.plusDays(8), null);
		UUID license = insertDocument("DRIVER_LICENSE", "DRIVER", newDriver("Conductor filtro"), "VALID",
				TODAY.plusDays(8), "B1");
		Set<UUID> mine = Set.of(soat, inspection, license);

		// Act + Assert (criterio 4)
		assertThat(mine(query(30, DocumentTypeCode.SOAT, null), mine)).extracting(ExpiringDocumentView::documentId)
			.containsExactly(soat);
		assertThat(mine(query(30, null, OwnerType.DRIVER), mine)).extracting(ExpiringDocumentView::documentId)
			.containsExactly(license);
		assertThat(mine(query(30, DocumentTypeCode.DRIVER_LICENSE, OwnerType.VEHICLE), mine)).isEmpty();
	}

	@Test
	void should_paginate_consistently_with_the_total_count() {
		// Arrange: al menos tres documentos en el horizonte
		for (int i = 0; i < 3; i++) {
			insertDocument("SOAT", "VEHICLE", newVehicle("ITP00" + i), "VALID", TODAY.plusDays(12), null);
		}
		ExpiringSoonView all = this.useCase.findExpiringSoon(query(30, null, null));

		// Act
		ExpiringSoonView second = this.useCase
			.findExpiringSoon(new ExpiringSoonQuery(30, null, null, new PageQuery(1, 1)));

		// Assert (criterio 5)
		long total = all.documents().totalElements();
		assertThat(total).isGreaterThanOrEqualTo(3).isEqualTo(all.documents().content().size());
		assertThat(second.documents().totalElements()).isEqualTo(total);
		assertThat(second.documents().totalPages()).isEqualTo((int) total);
		assertThat(second.documents().content()).containsExactly(all.documents().content().get(1));
		assertThat(second.horizonDays()).isEqualTo(30);
		assertThat(second.evaluatedOn()).isEqualTo(TODAY);
	}

	private static ExpiringSoonQuery query(int days, DocumentTypeCode documentType, OwnerType ownerType) {
		return new ExpiringSoonQuery(days, documentType, ownerType, ALL);
	}

	private List<ExpiringDocumentView> mine(ExpiringSoonQuery query, Set<UUID> documents) {
		return this.useCase.findExpiringSoon(query)
			.documents()
			.content()
			.stream()
			.filter(document -> documents.contains(document.documentId()))
			.toList();
	}

	private UUID newVehicle(String licensePlate) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO document.vehicle_replica (vehicle_id, license_plate, is_active, last_event_at) "
				+ "VALUES (?, ?, true, now())", id, licensePlate);
		return id;
	}

	private UUID newDriver(String fullName) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO document.driver_replica (driver_id, full_name, is_active, last_event_at) "
				+ "VALUES (?, ?, true, now())", id, fullName);
		return id;
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
				"EXS-" + id.toString().substring(0, 18), TODAY.minusYears(1), expirationDate, status, licenseCategory,
				cancelled ? "Registrado por error en la prueba" : null,
				cancelled ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
		return id;
	}

}
