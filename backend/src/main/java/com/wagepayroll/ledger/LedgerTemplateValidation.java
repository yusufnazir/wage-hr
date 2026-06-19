package com.wagepayroll.ledger;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.wagepayroll.api.dto.PlatformLedgerTemplateTranslationRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class LedgerTemplateValidation {

	private static final Set<String> REQUIRED_TRANSLATION_LOCALES = Set.of("en", "nl");

	private LedgerTemplateValidation() {
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

	public static String requireCode(String raw, String field) {
		if (raw == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
		}
		String t = raw.trim();
		if (t.isEmpty() || t.length() > 64) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " invalid");
		}
		return t;
	}

	public static String requireDescription(String raw, String field) {
		if (raw == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
		}
		String t = raw.trim();
		if (t.isEmpty() || t.length() > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " invalid");
		}
		return t;
	}

	public static Map<String, String> normalizeLedgerDescriptionTranslations(List<PlatformLedgerTemplateTranslationRequest> rows) {
		if (rows == null || rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LEDGER_TEMPLATE_TRANSLATIONS_REQUIRED");
		}
		Map<String, String> out = new LinkedHashMap<>();
		Set<String> duplicates = new HashSet<>();
		for (PlatformLedgerTemplateTranslationRequest row : rows) {
			if (row == null || row.locale() == null || row.locale().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LEDGER_TEMPLATE_TRANSLATION_LOCALE_REQUIRED");
			}
			String locale = row.locale().trim().toLowerCase(Locale.ROOT);
			if (!REQUIRED_TRANSLATION_LOCALES.contains(locale)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LEDGER_TEMPLATE_TRANSLATION_LOCALE_UNSUPPORTED");
			}
			if (out.containsKey(locale)) {
				duplicates.add(locale);
			}
			out.put(locale, requireDescription(row.description(), "description[" + locale + "]"));
		}
		if (!duplicates.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LEDGER_TEMPLATE_TRANSLATION_LOCALE_DUPLICATE");
		}
		if (!out.keySet().containsAll(REQUIRED_TRANSLATION_LOCALES)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LEDGER_TEMPLATE_TRANSLATIONS_REQUIRED");
		}
		return out;
	}
}
