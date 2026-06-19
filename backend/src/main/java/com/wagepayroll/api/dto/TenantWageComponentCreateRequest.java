package com.wagepayroll.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record TenantWageComponentCreateRequest(
		@NotNull UUID companyId,
		@NotNull UUID platformTemplateId,
		/** Appended after {@code template_code} with an underscore when non-blank. */
		String codeSuffix,
		String name) {
}
