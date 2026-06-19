package com.wagepayroll.payroll.engine;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One evaluated wage line for a tenant or platform component and employee during a payroll engine pass.
 */
public record EvaluatedComponentAmount(
		UUID employeeId,
		UUID tenantWageComponentId,
		String tenantWageComponentCode,
		String calculationMethod,
		BigDecimal evaluatedAmount,
		String formulaExpression,
		EvaluatedComponentSource componentSource,
		UUID platformWageComponentId) {

	public EvaluatedComponentAmount {
		if (componentSource == null) {
			componentSource = EvaluatedComponentSource.TENANT;
		}
	}

	public static EvaluatedComponentAmount tenant(UUID employeeId, UUID tenantWageComponentId, String code,
			String calculationMethod, BigDecimal evaluatedAmount, String formulaExpression) {
		return new EvaluatedComponentAmount(employeeId, tenantWageComponentId, code, calculationMethod, evaluatedAmount,
				formulaExpression, EvaluatedComponentSource.TENANT, null);
	}

	public static EvaluatedComponentAmount platform(UUID employeeId, UUID platformWageComponentId, String code,
			String calculationMethod, BigDecimal evaluatedAmount) {
		return new EvaluatedComponentAmount(employeeId, null, code, calculationMethod, evaluatedAmount, null,
				EvaluatedComponentSource.PLATFORM, platformWageComponentId);
	}
}
