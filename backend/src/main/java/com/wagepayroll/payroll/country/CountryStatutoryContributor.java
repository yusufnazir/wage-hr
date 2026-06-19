package com.wagepayroll.payroll.country;

import com.wagepayroll.payroll.engine.PayrollRunState;

/**
 * Country-specific statutory evaluation for phase {@code STATUTORY}.
 */
public interface CountryStatutoryContributor {

	boolean supports(String payrollCountryIso2);

	void contribute(PayrollRunState state);
}
