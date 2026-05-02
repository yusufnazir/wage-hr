package com.wagepayroll.api.dto;

import java.util.UUID;

public record TenantEmployeeGroupUpsertRequest(
		UUID companyId,
		String name,
		String code,
		String description,
		Boolean active) {
}
