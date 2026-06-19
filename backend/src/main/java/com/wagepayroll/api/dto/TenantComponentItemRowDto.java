package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TenantComponentItemRowDto(
		UUID id,
		UUID tenantComponentHeaderId,
		UUID tenantWageComponentId,
		String wageComponentCode,
		String wageComponentName,
		String name,
		String description,
		List<PlatformComponentTranslationDto> translations,
		int sortOrder,
		Instant createdAt,
		Instant updatedAt) {
}
