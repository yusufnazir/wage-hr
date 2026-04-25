package com.wagepayroll.billing;

import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.domain.billing.TenantResolutionState;

/**
 * Full payload persisted for each first-seen webhook idempotency key.
 */
public record WebhookReceiptInsert(String provider, String providerEventId, Instant receivedAt, String rawPayload, String eventType,
		TenantResolutionState tenantResolutionState, UUID tenantId, String tenantResolutionReasonCode,
		String tenantResolutionMissingFieldPath, String tenantResolutionResolverVersion) {
}
