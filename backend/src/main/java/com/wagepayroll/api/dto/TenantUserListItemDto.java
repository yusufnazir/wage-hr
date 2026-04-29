package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TenantUserListItemDto(UUID userId, String email, String status, Instant lastActiveAt, List<String> roleNames) {
}
