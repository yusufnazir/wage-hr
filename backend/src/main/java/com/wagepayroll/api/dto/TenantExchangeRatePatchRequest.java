package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TenantExchangeRatePatchRequest(UUID fromCurrencyId, UUID toCurrencyId, BigDecimal rate, LocalDate effectiveDate) {
}
