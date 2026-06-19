package com.wagepayroll.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TenantComponentGroupPutRequest(
		@NotNull Integer sortOrder,
		@NotNull Boolean active,
		@NotNull @Valid List<PlatformComponentTranslationRequest> translations) {
}
