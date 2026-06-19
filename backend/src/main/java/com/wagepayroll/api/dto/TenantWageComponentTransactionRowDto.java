package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantWageComponentTransactionRowDto(
		UUID id,
		UUID companyId,
		UUID employeeId,
		UUID payPeriodId,
		UUID payPeriodRunId,
		UUID tenantWageComponentId,
		String wageComponentCode,
		String wageComponentName,
		BigDecimal quantity,
		BigDecimal rate,
		BigDecimal amount,
		boolean manualOverride,
		String remarks,
		Instant createdAt,
		Instant updatedAt) {
}
