package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PlatformComponentItemPutRequest(
		@NotNull UUID platformWageComponentTemplateId,
		@NotNull Integer sortOrder,
		@NotNull @Valid List<PlatformComponentTranslationRequest> translations) {
}
