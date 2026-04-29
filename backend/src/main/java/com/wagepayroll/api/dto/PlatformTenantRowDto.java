package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformTenantRowDto(UUID id, String handle, String name, Instant createdAt, Instant updatedAt) {
}
