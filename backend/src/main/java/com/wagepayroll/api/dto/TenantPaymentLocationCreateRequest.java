package com.wagepayroll.api.dto;

import java.util.UUID;

public record TenantPaymentLocationCreateRequest(
		UUID companyId,
		String name,
		String paymentType,
		String currency,
		UUID bankTemplateId,
		String accountNumber) {
}
