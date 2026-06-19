package com.wagepayroll.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PlatformLedgerTemplateTranslationRequest(@NotBlank String locale, @NotBlank String description) {
}
