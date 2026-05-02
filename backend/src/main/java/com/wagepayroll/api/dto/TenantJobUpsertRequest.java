package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TenantJobUpsertRequest(
		UUID companyId,
		UUID departmentId,
		String title,
		String code,
		String description,
		String salaryType,
		BigDecimal defaultSalary,
		BigDecimal defaultHourlyRate,
		BigDecimal standardHoursPerWeek,
		String jobLevel,
		String jobCategory,
		Boolean active) {
}
