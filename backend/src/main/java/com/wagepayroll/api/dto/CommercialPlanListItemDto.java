package com.wagepayroll.api.dto;

import java.util.UUID;

public record CommercialPlanListItemDto(UUID id, String code, int sortOrder, boolean active, long featureCount,
		String stripeSubscriptionPriceId, String paypalBillingPlanId) {
}
