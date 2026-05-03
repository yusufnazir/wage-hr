package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record PlatformCountryDto(
		UUID id,
		String isoAlpha2,
		String isoAlpha3,
		String isoNumeric,
		String dialCode,
		boolean active,
		boolean payrollEnabled,
		String name,
		List<PlatformCountryTranslationDto> translations,
		String updatedAt) {
}
