package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

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

import co.edu.univalle.fleetops.document.service.application.port.in.PageQuery;
import co.edu.univalle.fleetops.document.service.application.port.in.PageResult;
import co.edu.univalle.fleetops.document.service.application.port.out.DocumentRepository.ExpiringDocumentSnapshot;
import co.edu.univalle.fleetops.document.service.domain.model.DocumentTypeCode;
import co.edu.univalle.fleetops.document.service.domain.model.OwnerType;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DocumentJpaRepository;
import co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.repository.DocumentJpaRepository.ExpiringDocumentRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentPersistenceAdapterExpiringSoonTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

	private static final UUID DOCUMENT_ID = UUID.fromString("019a2f4e-8b1c-7d2a-9e3f-1a2b3c4d5e6f");

	private static final UUID VEHICLE_ID = UUID.fromString("01960000-0000-7000-8000-000000000001");

	@Mock
	private DocumentJpaRepository repository;

	@InjectMocks
	private DocumentPersistenceAdapter adapter;

	@Test
	void should_search_active_documents_with_the_filters_as_catalog_names_and_map_the_page() {
		// Arrange: página 1 de 2 elementos por página, con 3 en total
		given(this.repository.findExpiringBetween(anyList(), eq(TODAY), eq(TODAY.plusDays(30)), eq("SOAT"),
				eq("VEHICLE"), any()))
			.willReturn(new PageImpl<>(List.of(row()), PageRequest.of(1, 2), 3));

		// Act
		PageResult<ExpiringDocumentSnapshot> page = this.adapter.findExpiringBetween(TODAY, TODAY.plusDays(30),
				DocumentTypeCode.SOAT, OwnerType.VEHICLE, new PageQuery(1, 2));

		// Assert: solo descarta REPLACED y CANCELLED; la fecha decide el resto
		verify(this.repository).findExpiringBetween(List.of("VALID", "EXPIRING_SOON", "EXPIRED"), TODAY,
				TODAY.plusDays(30), "SOAT", "VEHICLE", PageRequest.of(1, 2));
		assertThat(page.content()).containsExactly(new ExpiringDocumentSnapshot(DOCUMENT_ID, DocumentTypeCode.SOAT,
				OwnerType.VEHICLE, VEHICLE_ID, "AT-1300-0045-2026", TODAY.plusDays(10), "ABC123", null));
		assertThat(page).extracting(PageResult::page, PageResult::size, PageResult::totalElements,
				PageResult::totalPages)
			.containsExactly(1, 2, 3L, 2);
	}

	@Test
	void should_not_restrict_by_type_or_owner_when_the_filters_are_empty() {
		given(this.repository.findExpiringBetween(anyList(), any(), any(), isNull(), isNull(), any()))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		PageResult<ExpiringDocumentSnapshot> page = this.adapter.findExpiringBetween(TODAY, TODAY.plusDays(30), null,
				null, new PageQuery(0, 20));

		assertThat(page.content()).isEmpty();
		assertThat(page.totalElements()).isZero();
		assertThat(page.totalPages()).isZero();
	}

	private static ExpiringDocumentRow row() {
		return new ExpiringDocumentRow() {

			@Override
			public UUID getDocumentId() {
				return DOCUMENT_ID;
			}

			@Override
			public String getDocumentType() {
				return "SOAT";
			}

			@Override
			public String getOwnerType() {
				return "VEHICLE";
			}

			@Override
			public UUID getOwnerId() {
				return VEHICLE_ID;
			}

			@Override
			public String getDocumentNumber() {
				return "AT-1300-0045-2026";
			}

			@Override
			public LocalDate getExpirationDate() {
				return TODAY.plusDays(10);
			}

			@Override
			public String getLicensePlate() {
				return "ABC123";
			}

			@Override
			public String getFullName() {
				return null;
			}

		};
	}

}
