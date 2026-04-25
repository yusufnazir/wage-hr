package com.wagepayroll.api.dto;

import java.util.UUID;

public record UpsertTenantSubscriptionRequest(UUID commercialPlanId, String status) {
}
