package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record ReplaceCommercialPlanRequest(int sortOrder, boolean active, List<UUID> planFeatureIds,
		String stripeSubscriptionPriceId, Boolean clearStripeSubscriptionPrice, String paypalBillingPlanId,
		Boolean clearPaypalBillingPlanId) {
}
