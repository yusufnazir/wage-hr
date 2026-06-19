package com.wagepayroll.payroll.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SurinameFvoCalculatorTest {

	private final SurinameFvoCalculator calculator = new SurinameFvoCalculator(new SurinameWageTaxCalculator());

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void partyShareIsHalfPercentOfBasisloon() throws Exception {
		ObjectNode params = (ObjectNode) objectMapper.readTree(
				"{\"v\":2,\"freq\":\"MONTH\",\"kind\":\"FLAT_RATE\",\"pct\":0.5}");
		ResolvedSurinameTaxRule rule = new ResolvedSurinameTaxRule(UUID.randomUUID(),
				SurinameFvoCalculator.RULE_CODE, "FVO", LocalDate.of(2024, 1, 1), null, params);
		assertThat(calculator.computePartyShare(new BigDecimal("6000.0000"), rule))
				.isEqualByComparingTo("30.0000");
		assertThat(calculator.computePartyShare(new BigDecimal("0.0000"), rule))
				.isEqualByComparingTo("0.0000");
	}

}
