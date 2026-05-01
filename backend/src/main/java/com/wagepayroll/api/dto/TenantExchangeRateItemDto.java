package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TenantExchangeRateItemDto(UUID id, UUID fromCurrencyId, String fromCurrencyCode, String fromCurrencyDisplayName,
		UUID toCurrencyId, String toCurrencyCode, String toCurrencyDisplayName, BigDecimal rate, LocalDate effectiveDate,
		Instant createdAt, Instant updatedAt) {
}
