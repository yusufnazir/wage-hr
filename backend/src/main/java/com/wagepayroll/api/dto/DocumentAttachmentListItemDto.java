package com.wagepayroll.api.dto;

import java.time.Instant;

public record DocumentAttachmentListItemDto(String id, String entityType, String entityId, String createdByUserId,
		Instant createdAt) {
}
