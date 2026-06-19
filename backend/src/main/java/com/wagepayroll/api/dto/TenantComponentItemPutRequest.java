package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TenantComponentItemPutRequest(
		@NotNull UUID tenantWageComponentId,
		@NotNull Integer sortOrder,
		@NotNull @Valid List<PlatformComponentTranslationRequest> translations) {
}
