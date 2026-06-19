package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TenantPayrollYtdAccumulatorRowDto(
		String accumulatorCode,
		int taxYear,
		BigDecimal amount,
		String currencyIso3,
		Instant updatedAt) {
}
