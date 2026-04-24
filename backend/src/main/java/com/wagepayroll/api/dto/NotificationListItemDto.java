package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Strict columns only — no title/body (see {@code notifications-inbox.md}). */
public record NotificationListItemDto(UUID id, UUID tenantId, String notificationType, String templateVersion,
		UUID correlationId, String externalMessageId, String status, Instant readAt, Instant createdAt) {
}
