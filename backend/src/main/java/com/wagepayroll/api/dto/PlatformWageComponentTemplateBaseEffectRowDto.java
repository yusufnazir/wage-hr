package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PlatformWageComponentTemplateBaseEffectRowDto(
		UUID id,
		UUID payrollBaseId,
		String payrollBaseCode,
		String payrollBaseName,
		String effectDirection,
		String effectCalculationType,
		BigDecimal effectValue,
		int priority,
		boolean active) {
}
