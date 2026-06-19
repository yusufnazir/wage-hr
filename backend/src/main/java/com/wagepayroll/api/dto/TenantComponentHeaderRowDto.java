package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TenantComponentHeaderRowDto(
		UUID id,
		UUID tenantComponentGroupId,
		String name,
		String description,
		List<PlatformComponentTranslationDto> translations,
		int sortOrder,
		Instant createdAt,
		Instant updatedAt) {
}
