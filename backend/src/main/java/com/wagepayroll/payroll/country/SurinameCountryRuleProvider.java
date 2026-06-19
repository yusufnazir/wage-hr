package com.wagepayroll.payroll.country;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.wagepayroll.payroll.engine.CountryRuleContext;

@Component
public class SurinameCountryRuleProvider implements CountryRuleProvider {

	private static final String SR = "SR";

	private static final String PRIMARY_RULE_CODE = "SR_WAGE_TAX_DEFAULT";

	private final SurinameTaxRuleResolutionService taxRuleResolutionService;

	private final ObjectMapper objectMapper;

	public SurinameCountryRuleProvider(SurinameTaxRuleResolutionService taxRuleResolutionService,
			ObjectMapper objectMapper) {
		this.taxRuleResolutionService = taxRuleResolutionService;
		this.objectMapper = objectMapper;
	}

	@Override
	public String isoCountryCode() {
		return SR;
	}

	@Override
	public void contribute(CountryRuleContext context) {
		SurinameTaxRulesSnapshot snapshot = taxRuleResolutionService
				.resolveForSuriname(context.payroll().countryRulesAsOf());
		context.putAttribute(SurinameCountryContextKeys.TAX_RULES_SNAPSHOT, snapshot);
		context.putHint("sr.resolvedTaxRulesJson", snapshot.toJsonString(objectMapper));
		context.putHint("sr.resolvedTaxRuleCount", Integer.toString(snapshot.rulesByCode().size()));
		ResolvedSurinameTaxRule primary = snapshot.rulesByCode().get(PRIMARY_RULE_CODE);
		if (primary != null) {
			context.putHint("sr.primaryTaxRuleId", primary.id().toString());
			context.putHint("sr.primaryTaxRuleCode", primary.ruleCode());
		}
	}
}
