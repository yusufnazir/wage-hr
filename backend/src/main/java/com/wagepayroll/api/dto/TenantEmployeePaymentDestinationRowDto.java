package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantEmployeePaymentDestinationRowDto(
		UUID id,
		UUID companyId,
		UUID employeeId,
		String channelType,
		UUID paymentLocationId,
		String paymentLocationName,
		UUID bankTemplateId,
		String bankName,
		String accountNumber,
		String currency,
		String splitType,
		BigDecimal splitValue,
		int sortOrder,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
