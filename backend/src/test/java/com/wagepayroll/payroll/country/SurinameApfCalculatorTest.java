package com.wagepayroll.payroll.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest
@ActiveProfiles("test")
class SurinameApfCalculatorTest {

	@Autowired
	private SurinameApfCalculator calculator;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void clampsBaseAndAppliesHalfOf2026TotalRate() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromClasspath();
		assertThat(calculator.computePartyShare(new BigDecimal("7359.6155"), 2026, rule))
				.isEqualByComparingTo("212.5000");
		assertThat(calculator.computePartyShare(new BigDecimal("400.0000"), 2026, rule))
				.isEqualByComparingTo("21.2500");
	}

	@Test
	void usesFallbackScheduleBefore2025() {
		ResolvedSurinameTaxRule rule = new ResolvedSurinameTaxRule(UUID.randomUUID(),
				SurinameApfCalculator.RULE_CODE, "test", LocalDate.of(2024, 1, 1), null,
				objectMapper.createObjectNode());
		assertThat(calculator.computePartyShare(new BigDecimal("1000.0000"), 2023, rule))
				.isEqualByComparingTo("35.0000");
	}

	private ResolvedSurinameTaxRule ruleFromClasspath() throws Exception {
		byte[] bytes = getClass().getClassLoader()
				.getResourceAsStream("db/changelog/dml/suriname-apf-schedule-parameters.json")
				.readAllBytes();
		ObjectNode params = (ObjectNode) objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
		return new ResolvedSurinameTaxRule(UUID.randomUUID(), SurinameApfCalculator.RULE_CODE, "APF",
				LocalDate.of(2024, 1, 1), null, params);
	}

}
