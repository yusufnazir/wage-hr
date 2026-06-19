package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record TenantEmployeePaymentPeriodGroupDto(
		UUID payPeriodId,
		int year,
		String startDate,
		String endDate,
		String status,
		Integer periodNumber,
		List<TenantEmployeePayPeriodPaymentRowDto> payments) {
}
