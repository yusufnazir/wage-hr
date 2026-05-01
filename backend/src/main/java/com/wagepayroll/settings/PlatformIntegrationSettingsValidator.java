package com.wagepayroll.settings;

import java.util.Set;
import java.util.regex.Pattern;

import com.wagepayroll.api.dto.SettingEntryDto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Allow-listed keys for {@code PATCH /api/v1/platform/settings} beyond billing toggles (see
 * {@link com.wagepayroll.billing.BillingIntegrationSettingsValidator}). Unknown keys are rejected to avoid arbitrary
 * key/value growth.
 */
public final class PlatformIntegrationSettingsValidator {

	private static final Set<String> ALLOWED_KEYS = Set.of(
			"platform.product_name",
			"platform.application_name",
			"platform.base_url",
			"platform.date_format",
			"storage.minio.endpoint",
			"storage.minio.access_key",
			"storage.minio.secret_key",
			"storage.minio.bucket",
			"mail.api.base_url",
			"mail.api.project_key",
			"mail.api.username",
			"mail.api.password",
			"auth.registration.default_role_template_code");

	private static final Set<String> DATE_FORMATS = Set.of("yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "ISO-8601");

	/**
	 * Custom date-only patterns are allowed for operators. To keep formatting deterministic across clients, we only
	 * accept patterns that contain each of {@code yyyy}, {@code MM}, {@code dd} exactly once, with separators.
	 */
	private static final Pattern CUSTOM_DATE_FORMAT = Pattern.compile("^[yMd\\-/.\\s]{6,32}$");

	private PlatformIntegrationSettingsValidator() {
	}

	public static void validateIfKnownScope(SettingEntryDto entry) {
		if (entry == null || entry.key() == null) {
			return;
		}
		String key = entry.key();
		if (key.startsWith("billing.")) {
			return;
		}
		if (!ALLOWED_KEYS.contains(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_PLATFORM_SETTINGS_KEY");
		}
		if ("platform.date_format".equals(key)) {
			String v = entry.value();
			if (!isAllowedDateFormat(v)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PLATFORM_DATE_FORMAT");
			}
		}
	}

	static boolean isAllowedDateFormat(String raw) {
		if (raw == null || raw.isBlank()) {
			return false;
		}
		String v = raw.trim();
		if (DATE_FORMATS.contains(v)) {
			return true;
		}
		if (!CUSTOM_DATE_FORMAT.matcher(v).matches()) {
			return false;
		}
		if (countOccurrences(v, "yyyy") != 1 || countOccurrences(v, "MM") != 1 || countOccurrences(v, "dd") != 1) {
			return false;
		}
		// Reject ambiguous tokens.
		if (v.contains("yy") && !v.contains("yyyy")) {
			return false;
		}
		if (v.contains("M") && !v.contains("MM")) {
			return false;
		}
		if (v.contains("d") && !v.contains("dd")) {
			return false;
		}
		return true;
	}

	private static int countOccurrences(String s, String needle) {
		int n = 0;
		int i = 0;
		while (true) {
			int idx = s.indexOf(needle, i);
			if (idx < 0) {
				return n;
			}
			n++;
			i = idx + needle.length();
		}
	}
}
