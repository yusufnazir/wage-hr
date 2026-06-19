package com.wagepayroll.payroll.country;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.wagepayroll.domain.wagecomponent.PlatformCountryTaxRuleEntity;
import com.wagepayroll.domain.wagecomponent.PlatformCountryTaxRuleRepository;

@Service
public class SurinameTaxRuleResolutionService {

	private static final String SR = "SR";

	private final PlatformCountryTaxRuleRepository taxRuleRepository;

	private final ObjectMapper objectMapper;

	public SurinameTaxRuleResolutionService(PlatformCountryTaxRuleRepository taxRuleRepository,
			ObjectMapper objectMapper) {
		this.taxRuleRepository = taxRuleRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * Picks the active row per {@code rule_code} for Suriname: latest {@code effective_from}
	 * where {@code effective_from <= asOf} and {@code effective_to} is null or {@code >= asOf} (inclusive).
	 *
	 * @param countryRulesAsOf typically pay-period end; when null, uses UTC current date
	 */
	public SurinameTaxRulesSnapshot resolveForSuriname(LocalDate countryRulesAsOf) {
		LocalDate asOf = countryRulesAsOf != null ? countryRulesAsOf : LocalDate.now(ZoneOffset.UTC);
		List<PlatformCountryTaxRuleEntity> rows = taxRuleRepository.findByCountryCodeAndActiveIsTrue(SR);
		Map<String, List<PlatformCountryTaxRuleEntity>> byRule = rows.stream()
				.collect(Collectors.groupingBy(PlatformCountryTaxRuleEntity::getRuleCode));
		Map<String, ResolvedSurinameTaxRule> chosen = new TreeMap<>();
		for (var entry : byRule.entrySet()) {
			selectForAsOf(entry.getValue(), asOf).map(this::toResolved).ifPresent(r -> chosen.put(r.ruleCode(), r));
		}
		return new SurinameTaxRulesSnapshot(asOf, chosen);
	}

	private Optional<PlatformCountryTaxRuleEntity> selectForAsOf(List<PlatformCountryTaxRuleEntity> versions,
			LocalDate asOf) {
		return versions.stream()
				.filter(r -> !r.getEffectiveFrom().isAfter(asOf))
				.filter(r -> r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(asOf))
				.max(Comparator.comparing(PlatformCountryTaxRuleEntity::getEffectiveFrom));
	}

	private ResolvedSurinameTaxRule toResolved(PlatformCountryTaxRuleEntity e) {
		JsonNode params = readParameters(e.getParametersJson());
		return new ResolvedSurinameTaxRule(e.getId(), e.getRuleCode(), e.getName(), e.getEffectiveFrom(),
				e.getEffectiveTo(), params);
	}

	private JsonNode readParameters(String json) {
		if (json == null || json.isBlank()) {
			return objectMapper.createObjectNode();
		}
		try {
			return objectMapper.readTree(json);
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			return objectMapper.createObjectNode().put("parseError", "invalid parameters_json");
		}
	}
}
