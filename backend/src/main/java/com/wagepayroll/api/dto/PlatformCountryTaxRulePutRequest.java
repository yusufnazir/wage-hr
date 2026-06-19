package com.wagepayroll.api.dto;

import java.time.LocalDate;

public record PlatformCountryTaxRulePutRequest(
		String name,
		String parametersJson,
		LocalDate effectiveTo,
		Boolean active) {
}
