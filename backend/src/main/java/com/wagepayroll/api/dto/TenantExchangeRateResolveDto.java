package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TenantExchangeRateResolveDto(String fromCurrencyCode, String toCurrencyCode, BigDecimal rate,
		LocalDate effectiveDate) {
}
