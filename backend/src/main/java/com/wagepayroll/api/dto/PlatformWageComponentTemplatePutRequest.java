package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformWageComponentTemplatePutRequest(
		@NotBlank @Size(max = 200) String name,
		@Size(max = 500) String description,
		@NotBlank @Size(max = 4000) String definitionDefaultsJson,
		Integer processingOrderHint,
		@Size(max = 20) String phaseHint,
		UUID debitPlatformLedgerTemplateId,
		UUID creditPlatformLedgerTemplateId,
		boolean duplicable,
		boolean printOnPayslip,
		boolean auxiliary,
		boolean applyInPayroll,
		@Size(max = 20) String recurrence,
		@Size(max = 64) String countryRuleKey,
		UUID platformCountryTaxRuleId,
		boolean active,
		@Valid List<PlatformWageComponentTemplateBaseEffectPutItem> baseEffects,
		@Valid List<PlatformWageComponentTemplateDependencyPutItem> dependencies) {
}
