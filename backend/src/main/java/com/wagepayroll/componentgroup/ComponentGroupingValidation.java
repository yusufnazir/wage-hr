package com.wagepayroll.componentgroup;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.wagepayroll.api.dto.PlatformComponentTranslationRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ComponentGroupingValidation {

	private static final Set<String> REQUIRED_WRITE_LOCALES = Set.of("en", "nl");

	private static final Set<String> ALLOWED_WRITE_LOCALES = Set.of("en", "nl");

	private ComponentGroupingValidation() {
	}

	public record NameDescriptionPair(String name, String description) {
	}

	public static Map<String, NameDescriptionPair> normalizeTranslations(List<PlatformComponentTranslationRequest> rows) {
		if (rows == null || rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPONENT_GROUPING_TRANSLATIONS_REQUIRED");
		}
		Map<String, NameDescriptionPair> out = new LinkedHashMap<>();
		Set<String> duplicates = new HashSet<>();
		for (PlatformComponentTranslationRequest row : rows) {
			if (row == null || row.locale() == null || row.locale().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPONENT_GROUPING_TRANSLATION_LOCALE_REQUIRED");
			}
			String loc = row.locale().trim().toLowerCase(Locale.ROOT);
			if (!ALLOWED_WRITE_LOCALES.contains(loc)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPONENT_GROUPING_TRANSLATION_LOCALE_UNSUPPORTED");
			}
			if (out.containsKey(loc)) {
				duplicates.add(loc);
			}
			String name = requireName(row.name(), "name[" + loc + "]");
			String desc = normalizeOptionalDescription(row.description());
			out.put(loc, new NameDescriptionPair(name, desc));
		}
		if (!duplicates.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPONENT_GROUPING_TRANSLATION_LOCALE_DUPLICATE");
		}
		if (!out.keySet().containsAll(REQUIRED_WRITE_LOCALES)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPONENT_GROUPING_TRANSLATIONS_REQUIRED");
		}
		return out;
	}

	private static String requireName(String raw, String field) {
		if (raw == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
		}
		String t = raw.trim();
		if (t.isEmpty() || t.length() > 200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " invalid");
		}
		return t;
	}

	private static String normalizeOptionalDescription(String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim();
		if (t.isEmpty()) {
			return null;
		}
		if (t.length() > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description invalid");
		}
		return t;
	}
}
