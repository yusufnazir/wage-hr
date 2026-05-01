package com.wagepayroll.api.dto;

import java.util.UUID;

public record TenantCurrencyItemDto(UUID id, String code, String displayName, int sortOrder, boolean assigned) {
}
