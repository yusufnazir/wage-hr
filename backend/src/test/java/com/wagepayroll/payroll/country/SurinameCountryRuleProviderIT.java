package com.wagepayroll.payroll.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.wagepayroll.payroll.engine.CountryRuleContext;
import com.wagepayroll.payroll.engine.PayrollContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SurinameCountryRuleProviderIT {

	private static final UUID TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private static final UUID COMPANY = UUID.fromString("f0000000-0000-0000-0000-000000000001");

	@Autowired
	private SurinameCountryRuleProvider surinameCountryRuleProvider;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void beforeJuly2025ExcludesOvertimeTariff() throws Exception {
		var payroll = PayrollContext.withoutPinnedCountryRules(TENANT, COMPANY, "SR", "SRD", null, null, List.of())
				.withCountryRulesAsOf(LocalDate.of(2025, 1, 15));
		var ctx = new CountryRuleContext(payroll);
		surinameCountryRuleProvider.contribute(ctx);
		assertThat(ctx.hintsView().get("sr.primaryTaxRuleId")).isEqualTo("52000000-0000-0000-0000-000000000001");
		assertThat(ctx.hintsView().get("sr.primaryTaxRuleCode")).isEqualTo("SR_WAGE_TAX_DEFAULT");
		assertThat(ctx.hintsView().get("sr.resolvedTaxRuleCount")).isEqualTo("9");
		JsonNode root = objectMapper.readTree(ctx.hintsView().get("sr.resolvedTaxRulesJson"));
		assertThat(root.get("asOf").asText()).isEqualTo("2025-01-15");
		assertThat(root.get("rules").has("SR_OVERTIME_MONTH")).isFalse();
		assertThat(root.get("rules").has("SR_TAX_FREE_VACATION_YEAR")).isFalse();
		assertThat(root.get("rules").has("SR_TAX_FREE_BONUS_YEAR")).isFalse();
	}

	@Test
	void fromJuly2025IncludesOvertimeTariff() throws Exception {
		var payroll = PayrollContext.withoutPinnedCountryRules(TENANT, COMPANY, "SR", "SRD", null, null, List.of())
				.withCountryRulesAsOf(LocalDate.of(2025, 8, 1));
		var ctx = new CountryRuleContext(payroll);
		surinameCountryRuleProvider.contribute(ctx);
		assertThat(ctx.hintsView().get("sr.resolvedTaxRuleCount")).isEqualTo("12");
		JsonNode root = objectMapper.readTree(ctx.hintsView().get("sr.resolvedTaxRulesJson"));
		assertThat(root.get("rules").has("SR_OVERTIME_MONTH")).isTrue();
		assertThat(root.get("rules").get("SR_OVERTIME_MONTH").get("parameters").get("legacyTariffTypeId").asInt())
				.isEqualTo(3);
	}
}
