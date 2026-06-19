package com.wagepayroll.api.dto;

public record CompanyCalendarAdvanceResultDto(
		boolean advanced,
		Integer previousYear,
		Integer previousPeriod,
		Integer currentYear,
		Integer currentPeriod,
		String payPeriodEndDate) {
}
