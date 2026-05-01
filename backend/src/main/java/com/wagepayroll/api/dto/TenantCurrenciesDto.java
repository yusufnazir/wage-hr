package com.wagepayroll.api.dto;

import java.util.List;

public record TenantCurrenciesDto(List<TenantCurrencyItemDto> items, List<String> assignedCodes) {
}
