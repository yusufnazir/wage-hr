package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantWorkTimeItemDto(
		UUID id,
		UUID companyId,
		String name,
		String code,
		BigDecimal hoursPerDay,
		int workDaysPerWeek,
		String description,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
