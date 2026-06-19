package com.wagepayroll.payroll.country;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One {@code platform_country_tax_rule} row selected for a given calendar date.
 */
public record ResolvedSurinameTaxRule(UUID id, String ruleCode, String name, LocalDate effectiveFrom,
		LocalDate effectiveTo, JsonNode parameters) {
}
