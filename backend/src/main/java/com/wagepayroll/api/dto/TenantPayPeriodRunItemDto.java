package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantPayPeriodRunItemDto(
		UUID id,
		UUID payPeriodId,
		UUID tenantId,
		String runType,
		int runNumber,
		Instant createdAt,
		Instant updatedAt) {
}
