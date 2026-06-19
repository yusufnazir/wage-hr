package com.wagepayroll.payroll.base;

import java.math.BigDecimal;

import com.wagepayroll.payroll.model.PayrollBaseEffectDirection;

/**
 * One wage component's effect on a payroll base total (e.g. LOONBELASTING, GROSS).
 */
public record PayrollBaseContribution(
		String componentCode,
		String baseCode,
		PayrollBaseEffectDirection direction,
		BigDecimal componentAmount,
		BigDecimal baseDelta) {
}
