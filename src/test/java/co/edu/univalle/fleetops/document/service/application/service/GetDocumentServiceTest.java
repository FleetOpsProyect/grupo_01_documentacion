package co.edu.univalle.fleetops.document.service.application.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentRegistration;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.model.ValidityPeriod;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;
import co.edu.univalle.fleetops.document.service.domain.exception.ResourceNotFoundException;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class GetDocumentServiceTest {
	@Test
	void should_return_expired_when_document_expiration_date_has_passed() {

		// Arrange
		DocumentRepository repository = mock(DocumentRepository.class);
		BusinessClock clock = mock(BusinessClock.class);

		DocumentStatusCalculator calculator =
				new DocumentStatusCalculator(30);

		GetDocumentService service =
				new GetDocumentService(repository, clock, calculator);

		LocalDate today = LocalDate.of(2026, 11, 23);
		given(clock.today()).willReturn(today);

		UUID documentId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();

		DocumentRegistration registration = new DocumentRegistration(
				documentId,
				new OwnerRef(OwnerType.VEHICLE, ownerId),
				new DocumentNumber("SOAT-12345"),
				"Aseguradora Ejemplo",
				new ValidityPeriod(
						LocalDate.of(2025, 11, 20),
						LocalDate.of(2026, 11, 20)),
				null);

		Document document = Document.reconstitute(
				registration,
				DocumentTypeCode.SOAT,
				DocumentStatus.VALID,
				Instant.parse("2025-11-20T10:00:00Z"),
				Instant.parse("2025-11-20T10:00:00Z"),
				null,
				null,
				null);

		given(repository.findById(documentId))
				.willReturn(Optional.of(document));

		// Act
		DocumentView result = service.getById(documentId);

		// Assert
		assertEquals(DocumentStatus.EXPIRED, result.status());
		assertEquals(-3, result.daysRemaining());
		assertEquals(DocumentStatus.VALID, document.status());
	}

	@Test
	void should_preserve_cancelled_status() {

		// Arrange
		DocumentRepository repository = mock(DocumentRepository.class);
		BusinessClock clock = mock(BusinessClock.class);

		DocumentStatusCalculator calculator =
				new DocumentStatusCalculator(30);

		GetDocumentService service =
				new GetDocumentService(repository, clock, calculator);

		LocalDate today = LocalDate.of(2026, 11, 23);
		given(clock.today()).willReturn(today);

		UUID documentId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();

		DocumentRegistration registration = new DocumentRegistration(
				documentId,
				new OwnerRef(OwnerType.VEHICLE, ownerId),
				new DocumentNumber("SOAT-12345"),
				"Aseguradora Ejemplo",
				new ValidityPeriod(
						LocalDate.of(2025, 12, 30),
						LocalDate.of(2026, 12, 30)),
				null);

		Document document = Document.reconstitute(
				registration,
				DocumentTypeCode.SOAT,
				DocumentStatus.CANCELLED,
				Instant.parse("2025-11-20T10:00:00Z"),
				Instant.parse("2025-11-20T10:00:00Z"),
				null,
				null,
				null);

		given(repository.findById(documentId))
				.willReturn(Optional.of(document));

		// Act
		DocumentView result = service.getById(documentId);

		// Assert
		assertEquals(DocumentStatus.CANCELLED, result.status());
		assertNull(result.daysRemaining());
		assertEquals(DocumentStatus.CANCELLED, document.status());
	}

	@Test
	void should_preserve_replaced_status() {

		// Arrange
		DocumentRepository repository = mock(DocumentRepository.class);
		BusinessClock clock = mock(BusinessClock.class);

		DocumentStatusCalculator calculator =
				new DocumentStatusCalculator(30);

		GetDocumentService service =
				new GetDocumentService(repository, clock, calculator);

		LocalDate today = LocalDate.of(2026, 11, 23);
		given(clock.today()).willReturn(today);

		UUID documentId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();

		DocumentRegistration registration = new DocumentRegistration(
				documentId,
				new OwnerRef(OwnerType.VEHICLE, ownerId),
				new DocumentNumber("SOAT-12345"),
				"Aseguradora Ejemplo",
				new ValidityPeriod(
						LocalDate.of(2025, 12, 30),
						LocalDate.of(2026, 12, 30)),
				null);

		Document document = Document.reconstitute(
				registration,
				DocumentTypeCode.SOAT,
				DocumentStatus.REPLACED,
				Instant.parse("2025-11-20T10:00:00Z"),
				Instant.parse("2025-11-20T10:00:00Z"),
				null,
				null,
				null);

		given(repository.findById(documentId))
				.willReturn(Optional.of(document));

		// Act
		DocumentView result = service.getById(documentId);

		// Assert
		assertEquals(DocumentStatus.REPLACED, result.status());
		assertNull(result.daysRemaining());
		assertEquals(DocumentStatus.REPLACED, document.status());
	}

	@Test
	void should_return_null_days_remaining_when_document_has_no_expiration_date() {

		// Arrange
		DocumentRepository repository = mock(DocumentRepository.class);
		BusinessClock clock = mock(BusinessClock.class);

		DocumentStatusCalculator calculator =
				new DocumentStatusCalculator(30);

		GetDocumentService service =
				new GetDocumentService(repository, clock, calculator);

		LocalDate today = LocalDate.of(2026, 11, 23);
		given(clock.today()).willReturn(today);

		UUID documentId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();

		DocumentRegistration registration = new DocumentRegistration(
				documentId,
				new OwnerRef(OwnerType.VEHICLE, ownerId),
				new DocumentNumber("SOAT-12345"),
				"Aseguradora Ejemplo",
				new ValidityPeriod(
						LocalDate.of(2025, 11, 20),
						null),
				null);

		Document document = Document.reconstitute(
				registration,
				DocumentTypeCode.PROPERTY_CARD,
				DocumentStatus.VALID,
				Instant.parse("2025-11-20T10:00:00Z"),
				Instant.parse("2025-11-20T10:00:00Z"),
				null,
				null,
				null);

		given(repository.findById(documentId))
				.willReturn(Optional.of(document));

		// Act
		DocumentView result = service.getById(documentId);

		// Assert
		assertEquals(DocumentStatus.VALID, result.status());
		assertNull(result.daysRemaining());
		assertEquals(DocumentStatus.VALID, document.status());
	}

	@Test
	void should_return_expiring_soon_when_document_is_close_to_expiration() {

		// Arrange
		DocumentRepository repository = mock(DocumentRepository.class);
		BusinessClock clock = mock(BusinessClock.class);

		DocumentStatusCalculator calculator =
				new DocumentStatusCalculator(30);

		GetDocumentService service =
				new GetDocumentService(repository, clock, calculator);

		LocalDate today = LocalDate.of(2026, 11, 23);
		given(clock.today()).willReturn(today);

		UUID documentId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();

		DocumentRegistration registration = new DocumentRegistration(
				documentId,
				new OwnerRef(OwnerType.VEHICLE, ownerId),
				new DocumentNumber("SOAT-12345"),
				"Aseguradora Ejemplo",
				new ValidityPeriod(
						LocalDate.of(2025, 12, 3),
						LocalDate.of(2026, 12, 3)),
				null);

		Document document = Document.reconstitute(
				registration,
				DocumentTypeCode.SOAT,
				DocumentStatus.VALID,
				Instant.parse("2025-11-20T10:00:00Z"),
				Instant.parse("2025-11-20T10:00:00Z"),
				null,
				null,
				null);

		given(repository.findById(documentId))
				.willReturn(Optional.of(document));

		// Act
		DocumentView result = service.getById(documentId);

		// Assert
		assertEquals(DocumentStatus.EXPIRING_SOON, result.status());
		assertEquals(10, result.daysRemaining());
		assertEquals(DocumentStatus.VALID, document.status());
	}

	@Test
	void should_throw_exception_when_document_is_not_found() {

		// Arrange
		DocumentRepository repository = mock(DocumentRepository.class);
		BusinessClock clock = mock(BusinessClock.class);

		DocumentStatusCalculator calculator =
				new DocumentStatusCalculator(30);

		GetDocumentService service =
				new GetDocumentService(repository, clock, calculator);

		UUID documentId = UUID.randomUUID();

		given(repository.findById(documentId))
				.willReturn(Optional.empty());

		// Act
		ResourceNotFoundException exception = assertThrows(
				ResourceNotFoundException.class,
				() -> service.getById(documentId));

		// Assert
		assertEquals("Documento no encontrado", exception.getMessage());

		verify(repository).findById(documentId);
		verifyNoInteractions(clock);
	}

}
