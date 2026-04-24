package com.wagepayroll.notification;

import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.domain.notification.NotificationEntity;
import com.wagepayroll.domain.notification.NotificationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sole writer path for {@code notification} rows (see {@code docs/modules/notifications-inbox.md}).
 */
@Service
public class NotificationService {

	public static final String TEMPLATE_VERSION_M2_1 = "m2-1";

	private final NotificationRepository notificationRepository;

	public NotificationService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Transactional
	public void emit(NotificationEmitCommand command) {
		validate(command);
		if (command.requestEmailChannel()) {
			throw new IllegalArgumentException("requestEmailChannel not implemented for this emit path");
		}
		Instant now = Instant.now();
		NotificationEntity row = new NotificationEntity();
		row.setId(UUID.randomUUID());
		row.setTenantId(command.tenantId());
		row.setRecipientUserId(command.recipientUserId());
		row.setNotificationType(command.notificationType().code());
		row.setTemplateVersion(command.templateVersion());
		row.setCorrelationId(command.correlationId());
		row.setStatus(NotificationRowStatus.SENT.code());
		row.setCreatedAt(now);
		row.setUpdatedAt(now);
		notificationRepository.save(row);
	}

	@Transactional
	public void markRead(UUID tenantId, UUID readerUserId, UUID notificationId) {
		NotificationEntity row = notificationRepository
				.findByIdAndTenantIdAndRecipientUserId(notificationId, tenantId, readerUserId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND"));
		row.setReadAt(Instant.now());
		row.setStatus(NotificationRowStatus.READ.code());
		row.setUpdatedAt(Instant.now());
		notificationRepository.save(row);
	}

	private static void validate(NotificationEmitCommand c) {
		if (c.tenantId() == null || c.recipientUserId() == null || c.notificationType() == null
				|| c.templateVersion() == null || c.templateVersion().isBlank() || c.correlationId() == null) {
			throw new IllegalArgumentException("invalid emit command");
		}
	}
}
