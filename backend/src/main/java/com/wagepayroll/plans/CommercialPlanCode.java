package com.wagepayroll.plans;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes and validates {@code commercial_plan.code} (admin identifier, not marketing copy). */
public final class CommercialPlanCode {

	private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

	private CommercialPlanCode() {
	}

	public static String normalizeAndValidate(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("plan code required");
		}
		String c = raw.trim().toUpperCase(Locale.ROOT);
		if (!CODE.matcher(c).matches()) {
			throw new IllegalArgumentException("invalid plan code");
		}
		return c;
	}
}
