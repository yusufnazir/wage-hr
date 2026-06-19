package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlatformStatutoryWageComponentRowDto(
		UUID id,
		String countryCode,
		String code,
		String name,
		String description,
		boolean statutory,
		String componentType,
		String category,
		String netEffect,
		String calculationMethod,
		int processingOrder,
		String phase,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
