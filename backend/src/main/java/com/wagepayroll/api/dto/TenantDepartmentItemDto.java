package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantDepartmentItemDto(
		UUID id,
		UUID companyId,
		String name,
		String code,
		String description,
		UUID parentDepartmentId,
		UUID managerEmployeeId,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
