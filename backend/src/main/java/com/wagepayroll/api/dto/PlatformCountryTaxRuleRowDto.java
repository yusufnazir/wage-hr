package com.wagepayroll.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlatformCountryTaxRuleRowDto(
		UUID id,
		String countryCode,
		String ruleCode,
		String name,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		String parametersJson,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
