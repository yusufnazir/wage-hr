package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record PlatformWageComponentTemplateBaseEffectPutItem(
		@NotNull UUID payrollBaseId,
		@NotNull String effectDirection,
		@NotNull String effectCalculationType,
		BigDecimal effectValue,
		Integer priority) {
}
