package com.wagepayroll.api.dto;

import java.util.UUID;

public record TenantDepartmentUpsertRequest(
		UUID companyId,
		String name,
		String code,
		String description,
		UUID parentDepartmentId,
		UUID managerEmployeeId,
		Boolean active) {
}
