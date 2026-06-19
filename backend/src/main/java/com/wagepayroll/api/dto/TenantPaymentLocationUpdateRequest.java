package com.wagepayroll.api.dto;

import java.util.UUID;

public record TenantPaymentLocationUpdateRequest(
		String name,
		String currency,
		UUID bankTemplateId,
		String accountNumber) {
}
