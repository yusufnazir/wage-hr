package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record TenantPayPeriodFinalizeResultDto(UUID runId, int linesCreated, int employeeCount,
		Map<UUID, BigDecimal> employeeNetPay, int balancesUpdated, int postingsCreated,
		CompanyCalendarAdvanceResultDto calendarAdvance) {
}