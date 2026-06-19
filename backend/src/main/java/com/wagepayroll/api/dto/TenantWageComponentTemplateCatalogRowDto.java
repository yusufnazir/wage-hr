package com.wagepayroll.api.dto;

import java.util.UUID;

public record TenantWageComponentTemplateCatalogRowDto(
		UUID id,
		String countryCode,
		String templateCode,
		String name,
		String description,
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
		UUID platformCountryTaxRuleId) {
}
