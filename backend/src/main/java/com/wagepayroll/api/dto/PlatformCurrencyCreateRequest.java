package com.wagepayroll.api.dto;

public record PlatformCurrencyCreateRequest(String code, String displayName, Integer sortOrder, Boolean active) {
}
