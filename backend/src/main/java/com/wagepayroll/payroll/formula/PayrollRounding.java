package com.wagepayroll.payroll.formula;

import java.math.RoundingMode;

import com.wagepayroll.payroll.model.RoundingStrategy;

public final class PayrollRounding {

	private PayrollRounding() {
	}

	public static RoundingMode toRoundingMode(RoundingStrategy strategy) {
		if (strategy == null) {
			return RoundingMode.HALF_UP;
		}
		return switch (strategy) {
			case HALF_UP -> RoundingMode.HALF_UP;
			case HALF_EVEN -> RoundingMode.HALF_EVEN;
			case DOWN -> RoundingMode.DOWN;
			case UP -> RoundingMode.UP;
		};
	}
}
