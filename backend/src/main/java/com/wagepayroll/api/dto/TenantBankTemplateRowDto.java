package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantBankTemplateRowDto(
		UUID id,
		UUID companyId,
		UUID platformBankTemplateId,
		String countryCode,
		String platformTemplateName,
		String bankName,
		String swiftBic,
		String accountNumber,
		String currencyCode,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
