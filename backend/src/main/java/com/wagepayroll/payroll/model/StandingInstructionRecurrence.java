package com.wagepayroll.payroll.model;

/**
 * Recurrence for {@code tenant_employee_payroll_standing_instruction.recurrence}; v1 supports one value only.
 */
public enum StandingInstructionRecurrence {

	EACH_PAY_PERIOD;

	public static StandingInstructionRecurrence fromStored(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("recurrence required");
		}
		return StandingInstructionRecurrence.valueOf(value.trim());
	}
}
