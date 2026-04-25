package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PlanFeatureCatalogItemDto(UUID id, String code, int sortOrder, Instant createdAt, Instant updatedAt) {
}
