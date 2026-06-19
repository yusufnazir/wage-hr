package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TenantComponentGroupCreateRequest(
		@NotNull UUID companyId,
		UUID platformComponentGroupTemplateId,
		Integer sortOrder,
		Boolean active,
		@NotNull @Valid List<PlatformComponentTranslationRequest> translations) {
}
