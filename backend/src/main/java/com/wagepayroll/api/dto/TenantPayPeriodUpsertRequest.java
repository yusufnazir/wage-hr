package com.wagepayroll.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TenantPayPeriodUpsertRequest(
		UUID companyId,
		Integer year,
		LocalDate startDate,
		LocalDate endDate,
		String status) {
}
