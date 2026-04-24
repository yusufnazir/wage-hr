package com.wagepayroll.common.email;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes and validates invite/login email strings (ASCII-pragmatic; internationalized local-parts deferred).
 */
public final class EmailAddress {

	private static final int MAX_LEN = 320;
	/** Local@domain with reasonable character subsets; normalized to lowercase. */
	private static final Pattern ASCII_EMAIL = Pattern
			.compile("^[a-z0-9_+\\-.]{1,64}@[a-z0-9\\-.]{1,253}\\.[a-z]{2,63}$");

	private EmailAddress() {
	}

	public static String normalizeAndValidate(String raw) {
		if (raw == null) {
			throw new IllegalArgumentException("email required");
		}
		String trimmed = raw.trim().toLowerCase(Locale.ROOT);
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("email required");
		}
		if (trimmed.length() > MAX_LEN) {
			throw new IllegalArgumentException("email too long");
		}
		if (!ASCII_EMAIL.matcher(trimmed).matches()) {
			throw new IllegalArgumentException("invalid email format");
		}
		return trimmed;
	}
}
