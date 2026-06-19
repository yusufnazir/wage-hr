package com.wagepayroll.payroll.engine;

/**
 * Generic payroll calculation entry point. Phased processors and ledger writers plug in over time.
 */
public interface PayrollEngine {

	PayrollRunResult calculate(PayrollContext context);
}
