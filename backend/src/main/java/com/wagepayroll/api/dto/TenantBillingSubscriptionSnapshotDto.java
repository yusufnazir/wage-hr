package com.wagepayroll.api.dto;

import java.util.UUID;

/**
 * Snapshot of {@code tenant_subscription} for billing summary (no plan feature list; no provider ids).
 */
public record TenantBillingSubscriptionSnapshotDto(String status, UUID commercialPlanId, String commercialPlanCode) {
}
