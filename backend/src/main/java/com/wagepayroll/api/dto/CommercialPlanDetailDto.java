package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record CommercialPlanDetailDto(UUID id, String code, int sortOrder, boolean active, List<UUID> planFeatureIds,
		List<String> planFeatureCodes, String stripeSubscriptionPriceId, String paypalBillingPlanId) {
}
