package com.wagepayroll.payroll;

import java.math.BigDecimal;

import com.wagepayroll.payroll.model.BalanceDirection;
import com.wagepayroll.payroll.model.NetEffect;

/**
 * Computes signed balance deltas from payroll result line amounts.
 */
public final class PayrollBalanceChangeCalculator {

	private PayrollBalanceChangeCalculator() {
	}

	/**
	 * @param lineAmount rounded payroll amount (positive magnitude for earnings/deductions)
	 * @param balanceDirection component balance normal (DEBIT = amount owed / asset-style)
	 * @param netEffect how the line affects net pay
	 */
	public static BigDecimal computeChangeAmount(BigDecimal lineAmount, String balanceDirection, NetEffect netEffect) {
		if (lineAmount == null || lineAmount.signum() == 0 || netEffect == NetEffect.NO_EFFECT) {
			return BigDecimal.ZERO;
		}
		BigDecimal magnitude = lineAmount.abs();
		boolean debitNormal = BalanceDirection.DEBIT.name().equalsIgnoreCase(balanceDirection);
		return switch (netEffect) {
			case SUBTRACT_FROM_NET -> debitNormal ? magnitude.negate() : magnitude;
			case ADD_TO_NET -> debitNormal ? magnitude : magnitude.negate();
			case NO_EFFECT -> BigDecimal.ZERO;
		};
	}
}
