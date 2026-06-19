package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TenantEmployeeCompensationPutRequest(
		String currencyCode,
		String wageType,
		BigDecimal wageAmount,
		UUID workTimeId,
		Boolean applyTaxes,
		Boolean applyTaxExempt,
		Boolean applyAov,
		String notes) {
}
