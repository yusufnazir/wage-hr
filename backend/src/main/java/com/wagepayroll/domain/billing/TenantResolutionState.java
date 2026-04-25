package com.wagepayroll.domain.billing;

/**
 * Explicit outcome of mapping a provider webhook to a {@code tenant_id} via {@code billing_provider_link}.
 */
public enum TenantResolutionState {

	/** Matched {@code billing_provider_link}; {@code tenant_id} is set on the receipt. */
	RESOLVED,

	/** Webhook did not contain enough structured data to attempt a link lookup (e.g. missing payer id). */
	UNRESOLVED_INSUFFICIENT_DATA,

	/** A stable external id was extracted but no matching {@code billing_provider_link} row exists. */
	UNRESOLVED_NO_MATCH
}
