package com.wagepayroll.api.dto;

import java.time.Instant;

public record DocumentShareListItemDto(String id, String granteeUserId, String granteeRoleId, String createdByUserId,
		Instant createdAt) {
}
