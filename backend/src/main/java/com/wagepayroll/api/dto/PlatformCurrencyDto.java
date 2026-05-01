package com.wagepayroll.api.dto;

import java.util.UUID;

public record PlatformCurrencyDto(UUID id, String code, String displayName, int sortOrder, boolean active, String updatedAt) {
}
