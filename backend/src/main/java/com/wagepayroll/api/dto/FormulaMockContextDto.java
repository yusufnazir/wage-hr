package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Mock bindings for stateless formula validate (Phase 6). All fields optional; omitted values default to zero.
 */
public record FormulaMockContextDto(
		BigDecimal compensationPeriodicRate,
		BigDecimal compensationIsHourly,
		BigDecimal compensationHourlyRate,
		BigDecimal transactionQuantity,
		BigDecimal transactionRate,
		BigDecimal transactionAmount,
		BigDecimal definitionDefaultAmount,
		Map<String, BigDecimal> componentAmounts) {
}
