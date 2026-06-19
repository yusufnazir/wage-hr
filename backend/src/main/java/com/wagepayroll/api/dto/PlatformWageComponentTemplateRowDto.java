package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlatformWageComponentTemplateRowDto(
		UUID id,
		String countryCode,
		String templateCode,
		String name,
		String description,
		String definitionDefaultsJson,
		Integer processingOrderHint,
		String phaseHint,
		UUID debitPlatformLedgerTemplateId,
		UUID creditPlatformLedgerTemplateId,
		boolean duplicable,
		boolean printOnPayslip,
		boolean auxiliary,
		boolean applyInPayroll,
		String recurrence,
		String countryRuleKey,
		UUID platformCountryTaxRuleId,
		boolean active,
		List<PlatformWageComponentTemplateBaseEffectRowDto> baseEffects,
		List<PlatformWageComponentTemplateDependencyRowDto> dependencies,
		Instant createdAt,
		Instant updatedAt) {
}
