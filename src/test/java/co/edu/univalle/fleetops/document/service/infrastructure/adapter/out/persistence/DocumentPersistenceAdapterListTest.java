package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import co.edu.univalle.fleetops.document.service.application.port.in.ListDocumentQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.PageQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.PageResult;
import co.edu.univalle.fleetops.document.service.domain.model.Document;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentStatus;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity.DocumentJpaEntity;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DocumentJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentPersistenceAdapterListTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

	private static final LocalDate ALERT_HORIZON = TODAY.plusDays(30);

	private static final UUID DOCUMENT_ID = UUID.randomUUID();

	private static final UUID OWNER_ID = UUID.randomUUID();

	@Mock
	private DocumentJpaRepository repository;

	@InjectMocks
	private DocumentPersistenceAdapter adapter;

	@Test
	void should_apply_all_filters_and_map_the_paginated_result() {
		// Arrange
		LocalDate from = TODAY.minusDays(30);
		LocalDate to = TODAY;

		ListDocumentQuery query = new ListDocumentQuery(
				OwnerType.VEHICLE, OWNER_ID, DocumentTypeCode.SOAT,
				DocumentStatus.EXPIRED, from, to, new PageQuery(1, 2));

		given(this.repository.findDocuments(
				"VEHICLE", OWNER_ID, "SOAT", "EXPIRED",
				from, to, TODAY, ALERT_HORIZON, PageRequest.of(1, 2)))
				.willReturn(new PageImpl<>(
						List.of(entity()), PageRequest.of(1, 2), 3));

		// Act
		PageResult<Document> result =
				this.adapter.findDocuments(query, TODAY, ALERT_HORIZON);

		// Assert
		verify(this.repository).findDocuments(
				"VEHICLE", OWNER_ID, "SOAT", "EXPIRED",
				from, to, TODAY, ALERT_HORIZON, PageRequest.of(1, 2));

		assertThat(result.content()).hasSize(1);

		Document document = result.content().getFirst();

		assertThat(document.id()).isEqualTo(DOCUMENT_ID);
		assertThat(document.status()).isEqualTo(DocumentStatus.VALID);

		assertThat(result.page()).isEqualTo(1);
		assertThat(result.size()).isEqualTo(2);
		assertThat(result.totalElements()).isEqualTo(3L);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void should_return_an_empty_page_when_no_documents_match() {
		// Arrange
		ListDocumentQuery query = new ListDocumentQuery(
				null, null, null, null, null, null,
				new PageQuery(0, 20));

		given(this.repository.findDocuments(
				null, null, null, null, null, null,
				TODAY, ALERT_HORIZON, PageRequest.of(0, 20)))
				.willReturn(new PageImpl<>(
						List.of(), PageRequest.of(0, 20), 0));

		// Act
		PageResult<Document> result =
				this.adapter.findDocuments(query, TODAY, ALERT_HORIZON);

		// Assert
		verify(this.repository).findDocuments(
				null, null, null, null, null, null,
				TODAY, ALERT_HORIZON, PageRequest.of(0, 20));

		assertThat(result.content()).isEmpty();
		assertThat(result.page()).isZero();
		assertThat(result.size()).isEqualTo(20);
		assertThat(result.totalElements()).isZero();
		assertThat(result.totalPages()).isZero();
	}

	private static DocumentJpaEntity entity() {
		Instant createdAt = Instant.parse("2025-11-20T15:00:00Z");

		return new DocumentJpaEntity(
				DOCUMENT_ID,
				"SOAT",
				"VEHICLE",
				OWNER_ID,
				"SOAT-12345",
				"Aseguradora Ejemplo",
				LocalDate.of(2025, 11, 20),
				LocalDate.of(2026, 11, 20),
				"VALID",
				null,
				createdAt,
				createdAt);
	}

}
