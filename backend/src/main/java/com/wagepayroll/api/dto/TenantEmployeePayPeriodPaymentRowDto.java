package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TenantEmployeePayPeriodPaymentRowDto(
		UUID id,
		UUID payPeriodId,
		UUID payPeriodRunId,
		int payPeriodYear,
		String payPeriodStartDate,
		String payPeriodEndDate,
		String payPeriodStatus,
		String channelType,
		UUID paymentLocationId,
		String paymentLocationName,
		UUID bankTemplateId,
		String bankName,
		String accountNumber,
		String currency,
		String splitType,
		BigDecimal splitValue,
		BigDecimal allocatedAmount) {
}
