package com.wagepayroll.billing;

import java.util.UUID;

import com.wagepayroll.domain.billing.TenantResolutionState;

/**
 * Deterministic tenant-resolution outcome for a single webhook acceptance (before idempotency dedup).
 */
public record TenantResolutionResult(TenantResolutionState state, UUID tenantId, String reasonCode, String missingFieldPath,
		String resolverVersion) {

	public static TenantResolutionResult resolved(UUID tenantId, String resolverVersion) {
		return new TenantResolutionResult(TenantResolutionState.RESOLVED, tenantId, null, null, resolverVersion);
	}

	public static TenantResolutionResult insufficientData(String reasonCode, String missingFieldPath, String resolverVersion) {
		return new TenantResolutionResult(TenantResolutionState.UNRESOLVED_INSUFFICIENT_DATA, null, reasonCode, missingFieldPath,
				resolverVersion);
	}

	public static TenantResolutionResult noMatch(String reasonCode, String resolverVersion) {
		return new TenantResolutionResult(TenantResolutionState.UNRESOLVED_NO_MATCH, null, reasonCode, null, resolverVersion);
	}
}
