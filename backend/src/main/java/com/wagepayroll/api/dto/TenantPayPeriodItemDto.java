package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantPayPeriodItemDto(
		UUID id,
		UUID companyId,
		int year,
		String startDate,
		String endDate,
		String status,
		Instant createdAt,
		Instant updatedAt) {
}
