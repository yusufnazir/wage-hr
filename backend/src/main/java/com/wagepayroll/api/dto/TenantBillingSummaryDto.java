package com.wagepayroll.api.dto;

/**
 * Non-secret integration snapshot for tenant billing UI (no provider customer ids).
 *
 * @param subscription {@code null} when the tenant has no {@code tenant_subscription} row
 */
public record TenantBillingSummaryDto(boolean stripeBillingEnabled, boolean paypalBillingEnabled, boolean stripeCustomerLinked,
		boolean paypalCustomerLinked, TenantBillingSubscriptionSnapshotDto subscription) {
}
