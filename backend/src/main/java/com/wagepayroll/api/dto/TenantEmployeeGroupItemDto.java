package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantEmployeeGroupItemDto(
		UUID id,
		UUID companyId,
		String name,
		String code,
		String description,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
