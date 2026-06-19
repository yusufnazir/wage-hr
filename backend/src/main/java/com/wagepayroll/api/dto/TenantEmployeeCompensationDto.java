package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantEmployeeCompensationDto(
		UUID id,
		UUID employeeId,
		UUID companyId,
		String currencyCode,
		String wageType,
		BigDecimal wageAmount,
		UUID workTimeId,
		String workTimeName,
		BigDecimal workTimeHoursPerDay,
		Integer workTimeDaysPerWeek,
		boolean applyTaxes,
		boolean applyTaxExempt,
		boolean applyAov,
		String notes,
		BigDecimal derivedYearlyAmount,
		BigDecimal derivedPeriodAmount,
		BigDecimal derivedMonthlyAmount,
		BigDecimal derivedHourlyAmount,
		Instant createdAt,
		Instant updatedAt) {
}
