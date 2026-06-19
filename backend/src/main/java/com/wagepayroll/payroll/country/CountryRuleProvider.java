package com.wagepayroll.payroll.country;

import com.wagepayroll.payroll.engine.CountryRuleContext;

/**
 * Country-specific payroll rules (tax tables, validations, statutory ordering hints). Implementations
 * stay free of tenant mutations; they only enrich {@link CountryRuleContext}.
 */
public interface CountryRuleProvider {

	/**
	 * ISO-3166-1 alpha-2 country this provider applies to.
	 */
	String isoCountryCode();

	void contribute(CountryRuleContext context);
}
