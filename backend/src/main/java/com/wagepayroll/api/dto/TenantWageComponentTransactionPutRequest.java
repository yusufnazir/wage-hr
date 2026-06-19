package com.wagepayroll.api.dto;

import java.math.BigDecimal;

public record TenantWageComponentTransactionPutRequest(
		BigDecimal quantity,
		BigDecimal rate,
		BigDecimal amount,
		Boolean manualOverride,
		String remarks) {
}
