package com.wagepayroll.billing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.util.StringUtils;

/**
 * Compact opaque id sent to PayPal as {@code custom_id} (max 127 chars) and echoed on subscription webhooks.
 */
public final class PaypalSubscriptionCustomId {

	public static final String PREFIX = "WAGE";

	private PaypalSubscriptionCustomId() {
	}

	public static String encode(UUID tenantId, UUID commercialPlanId) {
		return PREFIX + "|" + tenantId + "|" + commercialPlanId;
	}

	public static Optional<Decode> decode(String raw) {
		if (!StringUtils.hasText(raw)) {
			return Optional.empty();
		}
		String t = raw.trim();
		if (t.length() > 127) {
			return Optional.empty();
		}
		String[] parts = t.split("\\|", -1);
		if (parts.length != 3 || !PREFIX.equals(parts[0])) {
			return Optional.empty();
		}
		try {
			return Optional.of(new Decode(UUID.fromString(parts[1]), UUID.fromString(parts[2])));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	public record Decode(UUID tenantId, UUID commercialPlanId) {
	}
}
