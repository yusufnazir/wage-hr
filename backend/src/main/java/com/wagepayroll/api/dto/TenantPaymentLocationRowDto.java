package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantPaymentLocationRowDto(
		UUID id,
		UUID companyId,
		String name,
		String paymentType,
		String currency,
		UUID bankTemplateId,
		String bankTemplateName,
		String bankName,
		String swiftBic,
		String accountNumberFormat,
		String accountNumberMasked,
		String accountNumberFull,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
