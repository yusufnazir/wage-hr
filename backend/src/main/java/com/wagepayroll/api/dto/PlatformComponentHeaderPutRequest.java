package com.wagepayroll.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PlatformComponentHeaderPutRequest(
		@NotNull Integer sortOrder,
		@NotNull @Valid List<PlatformComponentTranslationRequest> translations) {
}
