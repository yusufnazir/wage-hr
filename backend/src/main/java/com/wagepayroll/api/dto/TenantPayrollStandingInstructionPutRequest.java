package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TenantPayrollStandingInstructionPutRequest(
		UUID companyId,
		UUID employeeId,
		UUID tenantWageComponentId,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		BigDecimal amount,
		BigDecimal quantity,
		BigDecimal rate,
		String recurrence,
		Boolean active,
		Boolean amountOverride,
		Boolean factorOverride,
		String remarks) {
}
