package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantPayrollResultLineRowDto(
		UUID id,
		UUID payPeriodRunId,
		UUID employeeId,
		String componentSource,
		UUID componentRefId,
		String phase,
		int processingOrderSnapshot,
		BigDecimal quantity,
		BigDecimal rate,
		BigDecimal amount,
		BigDecimal roundedAmount,
		Instant createdAt) {
}
