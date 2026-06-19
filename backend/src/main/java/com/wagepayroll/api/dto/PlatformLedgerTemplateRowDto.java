package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlatformLedgerTemplateRowDto(
		UUID id,
		String countryCode,
		String code,
		String description,
		List<PlatformLedgerTemplateTranslationDto> translations,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
