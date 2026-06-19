package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TenantPayrollStandingInstructionRowDto(
		UUID id,
		UUID companyId,
		UUID employeeId,
		UUID tenantWageComponentId,
		String wageComponentCode,
		String wageComponentName,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		BigDecimal amount,
		BigDecimal quantity,
		BigDecimal rate,
		String recurrence,
		boolean active,
		boolean amountOverride,
		boolean factorOverride,
		String remarks,
		Instant createdAt,
		Instant updatedAt) {
}
