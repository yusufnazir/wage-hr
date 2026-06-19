package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformBankTemplateRowDto(
		UUID id,
		String countryCode,
		String name,
		String bankName,
		String swiftBic,
		String bankCode,
		String accountNumberFormat,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
