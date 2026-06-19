package com.wagepayroll.api.dto;

import java.time.LocalDate;

public record PlatformCountryTaxRuleCreateRequest(
		String countryCode,
		String ruleCode,
		String name,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		String parametersJson,
		Boolean active) {
}
