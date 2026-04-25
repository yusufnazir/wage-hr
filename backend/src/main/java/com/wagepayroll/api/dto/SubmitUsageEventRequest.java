package com.wagepayroll.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitUsageEventRequest(@NotBlank @Size(max = 64) String metricKey,
		@NotNull @DecimalMin(value = "0.000001", inclusive = true) @Digits(integer = 13, fraction = 6) BigDecimal quantity,
		@NotBlank @Size(max = 255) String idempotencyKey) {
}
