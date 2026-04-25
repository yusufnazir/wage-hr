package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record CreateCommercialPlanRequest(String code, int sortOrder, Boolean active, List<UUID> planFeatureIds,
		String stripeSubscriptionPriceId, String paypalBillingPlanId) {
}
