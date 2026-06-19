package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PlatformComponentItemCreateRequest(
		@NotNull UUID platformWageComponentTemplateId,
		Integer sortOrder,
		@NotNull @Valid List<PlatformComponentTranslationRequest> translations) {
}
