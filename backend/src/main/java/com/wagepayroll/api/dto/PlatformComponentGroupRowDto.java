package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlatformComponentGroupRowDto(
		UUID id,
		UUID platformCountryId,
		String countryCode,
		String name,
		String description,
		List<PlatformComponentTranslationDto> translations,
		int sortOrder,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
