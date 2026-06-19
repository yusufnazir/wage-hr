package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TenantComponentGroupRowDto(
		UUID id,
		UUID companyId,
		UUID platformComponentGroupTemplateId,
		String countryCode,
		String name,
		String description,
		List<PlatformComponentTranslationDto> translations,
		int sortOrder,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
