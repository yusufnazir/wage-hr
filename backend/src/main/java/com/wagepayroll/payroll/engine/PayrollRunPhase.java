package com.wagepayroll.payroll.engine;

/**
 * Macro phases for a single {@link PayrollEngine#calculate(PayrollContext)} run (ADR-PE-001).
 * Execution order matches enum declaration order.
 */
public enum PayrollRunPhase {
	CONTEXT,
	GROSS_AND_BASES,
	STATUTORY,
	NET_AND_ACCUMULATORS
}
