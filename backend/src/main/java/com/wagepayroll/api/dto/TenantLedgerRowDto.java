package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantLedgerRowDto(
		UUID id,
		UUID companyId,
		UUID platformLedgerTemplateId,
		String code,
		String description,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
