package com.wagepayroll.payroll.formula;

public record EmployeeFormulaMatchContext(
		String wageType,
		String departmentCode,
		String jobCode) {
}
