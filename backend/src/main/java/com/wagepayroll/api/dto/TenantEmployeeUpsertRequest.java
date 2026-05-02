package com.wagepayroll.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TenantEmployeeUpsertRequest(
		UUID companyId,
		UUID departmentId,
		UUID jobId,
		UUID employeeGroupId,
		String firstName,
		String lastName,
		LocalDate dateOfBirth,
		LocalDate hireDate,
		String email,
		String phone,
		String status,
		Boolean active) {
}
