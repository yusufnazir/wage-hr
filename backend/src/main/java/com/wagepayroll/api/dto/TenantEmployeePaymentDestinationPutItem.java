package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TenantEmployeePaymentDestinationPutItem(
		String channelType,
		UUID paymentLocationId,
		UUID bankTemplateId,
		String accountNumber,
		String currency,
		String splitType,
		BigDecimal splitValue,
		Integer sortOrder,
		Boolean active) {
}
