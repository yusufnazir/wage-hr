package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record EvaluatedComponentAmountDto(
		UUID employeeId,
		UUID tenantWageComponentId,
		String tenantWageComponentCode,
		String calculationMethod,
		BigDecimal evaluatedAmount,
		String formulaExpression,
		String componentSource,
		UUID platformWageComponentId) {
}
