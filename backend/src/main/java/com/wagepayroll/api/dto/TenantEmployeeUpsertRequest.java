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
		Boolean active,
		String badgeNumber,
		String idNumber,
		String gender,
		String nationality,
		String placeOfBirth,
		String civilState,
		LocalDate resignationDate,
		String addressStreet,
		String addressNumber,
		String addressCity,
		String addressCountry,
		String addressPostalCode) {
}
