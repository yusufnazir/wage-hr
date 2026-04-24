package com.wagepayroll.notification;

import java.util.UUID;

public record NotificationEmitCommand(
		UUID tenantId,
		UUID recipientUserId,
		NotificationType notificationType,
		String templateVersion,
		UUID correlationId,
		boolean requestEmailChannel) {
}
