package com.wagepayroll.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlatformLedgerTemplateCreateRequest(
		@NotBlank String countryCode,
		@NotBlank String code,
		@NotNull @Valid List<PlatformLedgerTemplateTranslationRequest> translations,
		Boolean active) {
}
