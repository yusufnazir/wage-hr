package com.wagepayroll.document;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wagepayroll.config.MinioStorageProperties;
import com.wagepayroll.config.MinioStorageProperties.OrphanCleanup;
import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.domain.document.TenantDocumentRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class DocumentOrphanObjectCleanupService {

	private static final Logger log = LoggerFactory.getLogger(DocumentOrphanObjectCleanupService.class);

	private static final Pattern CANONICAL_DOCUMENT_KEY = Pattern
			.compile("^tenants/([0-9a-fA-F-]{36})/documents/([0-9a-fA-F-]{36})/.+$");

	private final MinioDocumentStorageGateway storageGateway;
	private final MinioStorageProperties storageProperties;
	private final TenantDocumentRepository tenantDocumentRepository;

	public DocumentOrphanObjectCleanupService(MinioDocumentStorageGateway storageGateway, MinioStorageProperties storageProperties,
			TenantDocumentRepository tenantDocumentRepository) {
		this.storageGateway = storageGateway;
		this.storageProperties = storageProperties;
		this.tenantDocumentRepository = tenantDocumentRepository;
	}

	/**
	 * Deletes (1) S3 objects under {@code tenants/} that match the canonical document key pattern, are older than
	 * configured age, and have no {@code tenant_document} row; (2) retries S3 delete for soft-deleted rows older than
	 * that age. No-op when orphan cleanup is disabled or the storage gateway is absent.
	 */
	public CleanupSummary runCleanup() {
		OrphanCleanup cfg = storageProperties.getOrphanCleanup();
		if (!cfg.isEnabled()) {
			return CleanupSummary.skipped("disabled");
		}
		MinioDocumentStorageGateway gateway = storageGateway;
		if (!gateway.isOperational()) {
			log.debug("document orphan cleanup skipped: MinIO gateway not available");
			return CleanupSummary.skipped("no_gateway");
		}
		Instant ageCutoff = Instant.now().minus(cfg.getMinObjectAge());
		int orphanDeletes = 0;
		int orphanScanKeys = 0;
		int softDeletedAttempts = 0;
		int softDeletedFailures = 0;
		String continuationToken = null;
		int maxKeys = cfg.getMaxKeysPerRun();
		while (orphanScanKeys < maxKeys) {
			int pageSize = Math.min(1000, maxKeys - orphanScanKeys);
			MinioDocumentStorageGateway.S3ObjectListPage page = gateway.listObjectsPage("tenants/", pageSize, continuationToken);
			for (MinioDocumentStorageGateway.S3ObjectListingItem item : page.contents()) {
				orphanScanKeys++;
				Matcher m = CANONICAL_DOCUMENT_KEY.matcher(item.key());
				if (!m.matches()) {
					continue;
				}
				if (!item.lastModified().isBefore(ageCutoff)) {
					continue;
				}
				UUID tenantId = UUID.fromString(m.group(1));
				if (tenantDocumentRepository.existsByTenantIdAndStorageKey(tenantId, item.key())) {
					continue;
				}
				try {
					gateway.deleteObject(item.key());
					orphanDeletes++;
				}
				catch (RuntimeException ex) {
					log.warn("orphan S3 delete failed key={}: {}", item.key(), ex.getMessage());
				}
			}
			if (page.contents().isEmpty() && page.nextContinuationToken() == null) {
				break;
			}
			continuationToken = page.nextContinuationToken();
			if (continuationToken == null) {
				break;
			}
		}

		var softBatch = tenantDocumentRepository.findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(ageCutoff,
				PageRequest.of(0, cfg.getSoftDeletedRetryMax()));
		for (TenantDocumentEntity doc : softBatch) {
			softDeletedAttempts++;
			try {
				gateway.deleteObject(doc.getStorageKey());
			}
			catch (RuntimeException ex) {
				softDeletedFailures++;
				log.warn("soft-deleted S3 retry failed documentId={} key={}: {}", doc.getId(), doc.getStorageKey(), ex.getMessage());
			}
		}

		if (orphanDeletes > 0 || softDeletedAttempts > 0) {
			log.info("document orphan cleanup: orphanScanKeys={} orphanDeletes={} softDeletedAttempts={} softDeletedFailures={}",
					orphanScanKeys, orphanDeletes, softDeletedAttempts, softDeletedFailures);
		}
		return new CleanupSummary(orphanScanKeys, orphanDeletes, softDeletedAttempts, softDeletedFailures, false, null);
	}

	public record CleanupSummary(int orphanScanKeys, int orphanDeletes, int softDeletedAttempts, int softDeletedFailures,
			boolean skipped, String skipReason) {

		static CleanupSummary skipped(String reason) {
			return new CleanupSummary(0, 0, 0, 0, true, reason);
		}
	}
}
