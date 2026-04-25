package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record TenantSubscriptionPayloadDto(UUID tenantId, UUID commercialPlanId, String planCode, String status,
		List<UUID> planFeatureIds, List<String> planFeatureCodes) {
}
