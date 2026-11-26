package co.edu.univalle.fleetops.document.service.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.univalle.fleetops.document.service.application.port.in.DocumentView;
import co.edu.univalle.fleetops.document.service.application.port.in.ListDocumentQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.PageQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.PageResult;
import co.edu.univalle.fleetops.document.service.application.port.out.BusinessClock;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentNumber;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentRegistration;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerRef;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.domain.model.ValidityPeriod;
import co.edu.univalle.fleetops.document.service.domain.service.AlertThresholdPolicy;
import co.edu.univalle.fleetops.document.service.domain.service.DocumentStatusCalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ListDocumentsServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

	private static final int MAX_THRESHOLD = 30;

	private static final LocalDate ALERT_HORIZON = TODAY.plusDays(MAX_THRESHOLD);

	private static final ListDocumentQuery QUERY = new ListDocumentQuery(
			null, null, null, null, null, null, new PageQuery(0, 20));

	@Mock
	private DocumentRepository repository;

	@Mock
	private BusinessClock clock;

	private ListDocumentsService service;

	@BeforeEach
	void setUp() {
		DocumentStatusCalculator calculator =
				new DocumentStatusCalculator(MAX_THRESHOLD);

		AlertThresholdPolicy alertPolicy =
				new AlertThresholdPolicy(List.of(7, 15, MAX_THRESHOLD));

		this.service = new ListDocumentsService(
				this.repository, this.clock, calculator, alertPolicy);

		given(this.clock.today()).willReturn(TODAY);
	}

	@Test
	void should_recalculate_expired_documents() {
		Document document = document(
				DocumentStatus.VALID, TODAY.minusDays(6));

		DocumentView result = listSingleDocument(document);

		assertThat(result.status()).isEqualTo(DocumentStatus.EXPIRED);
		assertThat(result.daysRemaining()).isEqualTo(-6);
		assertThat(document.status()).isEqualTo(DocumentStatus.VALID);
	}

	@Test
	void should_return_zero_days_when_document_expires_today() {
		Document document = document(DocumentStatus.VALID, TODAY);

		DocumentView result = listSingleDocument(document);

		assertThat(result.status()).isEqualTo(DocumentStatus.EXPIRING_SOON);
		assertThat(result.daysRemaining()).isZero();
	}

	@Test
	void should_include_the_alert_threshold_in_expiring_soon() {
		Document document = document(
				DocumentStatus.VALID, ALERT_HORIZON);

		DocumentView result = listSingleDocument(document);

		assertThat(result.status()).isEqualTo(DocumentStatus.EXPIRING_SOON);
		assertThat(result.daysRemaining()).isEqualTo(MAX_THRESHOLD);
	}

	@Test
	void should_return_valid_beyond_the_alert_threshold() {
		Document document = document(
				DocumentStatus.EXPIRED, ALERT_HORIZON.plusDays(1));

		DocumentView result = listSingleDocument(document);

		assertThat(result.status()).isEqualTo(DocumentStatus.VALID);
		assertThat(result.daysRemaining()).isEqualTo(MAX_THRESHOLD + 1);
	}

	@Test
	void should_return_null_days_for_documents_without_expiration() {
		Document document = document(DocumentStatus.VALID, null);

		DocumentView result = listSingleDocument(document);

		assertThat(result.documentType())
				.isEqualTo(DocumentTypeCode.PROPERTY_CARD);

		assertThat(result.status()).isEqualTo(DocumentStatus.VALID);
		assertThat(result.daysRemaining()).isNull();
	}

	@Test
	void should_preserve_replaced_documents() {
		Document document = document(
				DocumentStatus.REPLACED, TODAY.minusDays(6));

		DocumentView result = listSingleDocument(document);

		assertThat(result.status()).isEqualTo(DocumentStatus.REPLACED);
		assertThat(result.daysRemaining()).isNull();
	}

	@Test
	void should_preserve_cancelled_documents() {
		Document document = document(
				DocumentStatus.CANCELLED, TODAY.plusDays(60));

		ListDocumentQuery query = new ListDocumentQuery(
				null, null, null, DocumentStatus.CANCELLED,
				null, null, new PageQuery(0, 20));

		given(this.repository.findDocuments(query, TODAY, ALERT_HORIZON))
				.willReturn(new PageResult<>(List.of(document), 0, 20, 1, 1));

		PageResult<DocumentView> result = this.service.listDocuments(query);

		assertThat(result.content()).hasSize(1);
		assertThat(result.content().getFirst().status())
				.isEqualTo(DocumentStatus.CANCELLED);
		assertThat(result.content().getFirst().daysRemaining()).isNull();
	}

	@Test
	void should_preserve_pagination_and_document_order() {
		Document expired = document(
				DocumentStatus.VALID, TODAY.minusDays(6));

		Document expiringSoon = document(
				DocumentStatus.VALID, TODAY.plusDays(10));

		ListDocumentQuery query = new ListDocumentQuery(
				null, null, null, null, null, null, new PageQuery(1, 2));

		given(this.repository.findDocuments(query, TODAY, ALERT_HORIZON))
				.willReturn(new PageResult<>(
						List.of(expired, expiringSoon), 1, 2, 5, 3));

		PageResult<DocumentView> result = this.service.listDocuments(query);

		assertThat(result.content())
				.extracting(DocumentView::documentId)
				.containsExactly(expired.id(), expiringSoon.id());

		assertThat(result.content())
				.extracting(DocumentView::status)
				.containsExactly(
						DocumentStatus.EXPIRED, DocumentStatus.EXPIRING_SOON);

		assertThat(result.page()).isEqualTo(1);
		assertThat(result.size()).isEqualTo(2);
		assertThat(result.totalElements()).isEqualTo(5L);
		assertThat(result.totalPages()).isEqualTo(3);

		verify(this.clock).today();
		verify(this.repository).findDocuments(query, TODAY, ALERT_HORIZON);
		verifyNoMoreInteractions(this.clock, this.repository);
	}

	@Test
	void should_return_an_empty_page_when_no_documents_match() {
		given(this.repository.findDocuments(QUERY, TODAY, ALERT_HORIZON))
				.willReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

		PageResult<DocumentView> result = this.service.listDocuments(QUERY);

		assertThat(result.content()).isEmpty();
		assertThat(result.page()).isZero();
		assertThat(result.size()).isEqualTo(20);
		assertThat(result.totalElements()).isZero();
		assertThat(result.totalPages()).isZero();
	}

	@Test
	void should_use_the_configured_alert_threshold() {
		DocumentStatusCalculator calculator = new DocumentStatusCalculator(45);
		AlertThresholdPolicy alertPolicy =
				new AlertThresholdPolicy(List.of(7, 15, 45));

		ListDocumentsService customService = new ListDocumentsService(
				this.repository, this.clock, calculator, alertPolicy);

		Document document = document(
				DocumentStatus.VALID, TODAY.plusDays(40));

		given(this.repository.findDocuments(QUERY, TODAY, TODAY.plusDays(45)))
				.willReturn(new PageResult<>(List.of(document), 0, 20, 1, 1));

		PageResult<DocumentView> result = customService.listDocuments(QUERY);

		assertThat(result.content().getFirst().status())
				.isEqualTo(DocumentStatus.EXPIRING_SOON);

		assertThat(result.content().getFirst().daysRemaining()).isEqualTo(40);

		verify(this.repository).findDocuments(QUERY, TODAY, TODAY.plusDays(45));
	}

	private DocumentView listSingleDocument(Document document) {
		given(this.repository.findDocuments(QUERY, TODAY, ALERT_HORIZON))
				.willReturn(new PageResult<>(List.of(document), 0, 20, 1, 1));

		return this.service.listDocuments(QUERY).content().getFirst();
	}

	private static Document document(DocumentStatus status, LocalDate expirationDate) {
		DocumentTypeCode type = (expirationDate == null)
				? DocumentTypeCode.PROPERTY_CARD : DocumentTypeCode.SOAT;

		DocumentRegistration registration = new DocumentRegistration(
				UUID.randomUUID(),
				new OwnerRef(OwnerType.VEHICLE, UUID.randomUUID()),
				new DocumentNumber("DOC-12345"),
				"Entidad Ejemplo",
				new ValidityPeriod(LocalDate.of(2025, 11, 20), expirationDate),
				null);

		Instant createdAt = Instant.parse("2025-11-20T10:00:00Z");

		return Document.reconstitute(
				registration, type, status, createdAt, createdAt,
				null, null, null);
	}

}
