package com.wagepayroll.api.dto;

import java.util.List;

public record TenantEmployeePaymentOverviewDto(
		List<TenantEmployeePaymentDestinationRowDto> destinations,
		TenantEmployeePaymentPeriodGroupDto activePeriod,
		List<TenantEmployeePaymentPeriodGroupDto> closedPeriods) {
}
