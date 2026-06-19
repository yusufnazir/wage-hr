package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TenantPayrollStandingInstructionCreateRequest(
		UUID companyId,
		UUID employeeId,
		UUID tenantWageComponentId,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		BigDecimal amount,
		BigDecimal quantity,
		BigDecimal rate,
		String recurrence,
		Boolean amountOverride,
		Boolean factorOverride,
		String remarks) {
}
