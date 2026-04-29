package com.wagepayroll.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.wagepayroll.config.MinioStorageProperties;
import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.domain.document.TenantDocumentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentOrphanObjectCleanupServiceTest {

	@Mock
	private MinioDocumentStorageGateway gateway;

	@Mock
	private TenantDocumentRepository tenantDocumentRepository;

	private MinioStorageProperties storageProperties;

	@BeforeEach
	void setUp() {
		storageProperties = new MinioStorageProperties();
		storageProperties.setEndpoint("http://127.0.0.1:9000");
		storageProperties.setAccessKey("access");
		storageProperties.setSecretKey("secret");
		storageProperties.setBucket("bucket");
		storageProperties.getOrphanCleanup().setEnabled(true);
		storageProperties.getOrphanCleanup().setMinObjectAge(Duration.ofHours(1));
		storageProperties.getOrphanCleanup().setMaxKeysPerRun(500);
		storageProperties.getOrphanCleanup().setSoftDeletedRetryMax(50);
		when(gateway.isOperational()).thenReturn(true);
		lenient().when(gateway.listObjectsPage(any(), anyInt(), any()))
				.thenReturn(new MinioDocumentStorageGateway.S3ObjectListPage(List.of(), null));
		lenient().when(tenantDocumentRepository.findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(any(), any()))
				.thenReturn(List.of());
	}

	@Test
	void deletesOrphanWhenNoDbRowAndObjectOldEnough() {
		UUID tenantId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		String key = "tenants/" + tenantId + "/documents/" + documentId + "/f.pdf";
		Instant old = Instant.now().minus(2, ChronoUnit.HOURS);
		when(gateway.listObjectsPage(eq("tenants/"), eq(500), isNull())).thenReturn(
				new MinioDocumentStorageGateway.S3ObjectListPage(List.of(new MinioDocumentStorageGateway.S3ObjectListingItem(key, old)), null));
		when(tenantDocumentRepository.existsByTenantIdAndStorageKey(tenantId, key)).thenReturn(false);

		DocumentOrphanObjectCleanupService svc = new DocumentOrphanObjectCleanupService(gateway, storageProperties, tenantDocumentRepository);
		DocumentOrphanObjectCleanupService.CleanupSummary summary = svc.runCleanup();

		assertThat(summary.skipped()).isFalse();
		assertThat(summary.orphanScanKeys()).isEqualTo(1);
		assertThat(summary.orphanDeletes()).isEqualTo(1);
		verify(gateway).deleteObject(key);
	}

	@Test
	void doesNotDeleteWhenRowExists() {
		UUID tenantId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		String key = "tenants/" + tenantId + "/documents/" + documentId + "/f.pdf";
		Instant old = Instant.now().minus(2, ChronoUnit.HOURS);
		when(gateway.listObjectsPage(eq("tenants/"), eq(500), isNull())).thenReturn(
				new MinioDocumentStorageGateway.S3ObjectListPage(List.of(new MinioDocumentStorageGateway.S3ObjectListingItem(key, old)), null));
		when(tenantDocumentRepository.existsByTenantIdAndStorageKey(tenantId, key)).thenReturn(true);

		DocumentOrphanObjectCleanupService svc = new DocumentOrphanObjectCleanupService(gateway, storageProperties, tenantDocumentRepository);
		svc.runCleanup();

		verify(gateway, never()).deleteObject(key);
	}

	@Test
	void doesNotDeleteWhenObjectTooNew() {
		UUID tenantId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		String key = "tenants/" + tenantId + "/documents/" + documentId + "/f.pdf";
		Instant recent = Instant.now().minus(5, ChronoUnit.MINUTES);
		when(gateway.listObjectsPage(eq("tenants/"), eq(500), isNull())).thenReturn(new MinioDocumentStorageGateway.S3ObjectListPage(
				List.of(new MinioDocumentStorageGateway.S3ObjectListingItem(key, recent)), null));
		when(tenantDocumentRepository.existsByTenantIdAndStorageKey(tenantId, key)).thenReturn(false);

		DocumentOrphanObjectCleanupService svc = new DocumentOrphanObjectCleanupService(gateway, storageProperties, tenantDocumentRepository);
		svc.runCleanup();

		verify(gateway, never()).deleteObject(key);
	}

	@Test
	void retriesS3DeleteForOldSoftDeletedRows() {
		UUID tenantId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		String key = "tenants/" + tenantId + "/documents/" + documentId + "/f.pdf";
		when(gateway.listObjectsPage(eq("tenants/"), eq(500), isNull()))
				.thenReturn(new MinioDocumentStorageGateway.S3ObjectListPage(List.of(), null));
		TenantDocumentEntity soft = new TenantDocumentEntity();
		soft.setId(documentId);
		soft.setTenantId(tenantId);
		soft.setStorageKey(key);
		soft.setDeletedAt(Instant.now().minus(2, ChronoUnit.HOURS));
		when(tenantDocumentRepository.findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(any(), any(Pageable.class)))
				.thenReturn(List.of(soft));

		DocumentOrphanObjectCleanupService svc = new DocumentOrphanObjectCleanupService(gateway, storageProperties, tenantDocumentRepository);
		DocumentOrphanObjectCleanupService.CleanupSummary summary = svc.runCleanup();

		assertThat(summary.softDeletedAttempts()).isEqualTo(1);
		verify(gateway).deleteObject(key);
	}

	@Test
	void skippedWhenDisabled() {
		storageProperties.getOrphanCleanup().setEnabled(false);
		DocumentOrphanObjectCleanupService svc = new DocumentOrphanObjectCleanupService(gateway, storageProperties, tenantDocumentRepository);
		DocumentOrphanObjectCleanupService.CleanupSummary summary = svc.runCleanup();
		assertThat(summary.skipped()).isTrue();
		verify(gateway, never()).listObjectsPage(any(), anyInt(), any());
	}
}
