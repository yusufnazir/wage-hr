package com.wagepayroll.document;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UTC scheduled pass for {@link DocumentOrphanObjectCleanupService}. Bean is absent unless
 * {@code app.storage.minio.orphan-cleanup.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "app.storage.minio.orphan-cleanup", name = "enabled", havingValue = "true")
public class DocumentOrphanObjectCleanupScheduler {

	private final DocumentOrphanObjectCleanupService documentOrphanObjectCleanupService;

	public DocumentOrphanObjectCleanupScheduler(DocumentOrphanObjectCleanupService documentOrphanObjectCleanupService) {
		this.documentOrphanObjectCleanupService = documentOrphanObjectCleanupService;
	}

	@Scheduled(cron = "${app.storage.minio.orphan-cleanup.cron:0 30 4 * * *}", zone = "UTC")
	public void runOrphanCleanup() {
		documentOrphanObjectCleanupService.runCleanup();
	}
}
