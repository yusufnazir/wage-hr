package com.wagepayroll.settings;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class SettingsEntryValidator {

	private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]{1,128}");

	private SettingsEntryValidator() {
	}

	static void validateKey(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SETTINGS_KEY");
		}
	}

	static void validateValue(String value) {
		if (value == null || value.length() > 2000) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SETTINGS_VALUE");
		}
	}
}
