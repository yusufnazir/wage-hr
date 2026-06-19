package com.wagepayroll.banktemplate;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class BankTemplateValidation {

	public static final Pattern SWIFT_BIC = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");

	private BankTemplateValidation() {
	}

	public static String requireName(String raw, String field) {
		if (raw == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
		}
		String t = raw.trim();
		if (t.isEmpty() || t.length() > 150) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " invalid");
		}
		return t;
	}

	public static String trimBankName(String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim();
		return t.isEmpty() ? null : (t.length() > 150 ? t.substring(0, 150) : t);
	}

	public static String normalizeSwiftBicOrNull(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String t = raw.trim().toUpperCase(Locale.ROOT);
		if (!SWIFT_BIC.matcher(t).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SWIFT_BIC");
		}
		return t;
	}

	public static String trimBankCode(String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim();
		return t.isEmpty() ? null : (t.length() > 30 ? t.substring(0, 30) : t);
	}

	public static String trimAccountFormat(String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim();
		return t.isEmpty() ? null : (t.length() > 100 ? t.substring(0, 100) : t);
	}

	public static String normalizeIso2(String raw, String field) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
		}
		String t = raw.trim().toUpperCase(Locale.ROOT);
		if (t.length() != 2) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " invalid");
		}
		return t;
	}
}
