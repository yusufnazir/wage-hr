package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TenantWorkTimeUpsertRequest(
		UUID companyId,
		String name,
		String code,
		BigDecimal hoursPerDay,
		Integer workDaysPerWeek,
		String description,
		Boolean active) {
}
