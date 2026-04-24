package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantInvitationListItemDto(UUID id, String invitedEmail, UUID roleId, String status, Instant expiresAt,
		Instant createdAt) {
}
