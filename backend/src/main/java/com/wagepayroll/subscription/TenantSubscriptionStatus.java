package com.wagepayroll.subscription;

public enum TenantSubscriptionStatus {

	ACTIVE,
	CANCELLED;

	public String code() {
		return name();
	}

	public static TenantSubscriptionStatus parse(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("status required");
		}
		return TenantSubscriptionStatus.valueOf(raw.trim().toUpperCase());
	}
}
