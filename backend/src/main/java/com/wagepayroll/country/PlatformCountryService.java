package com.wagepayroll.country;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.wagepayroll.api.dto.PlatformCountryDto;
import com.wagepayroll.api.dto.PlatformCountryTranslationDto;
import com.wagepayroll.api.dto.PlatformCountryTranslationRequest;
import com.wagepayroll.api.dto.PlatformCountryUpsertRequest;
import com.wagepayroll.domain.country.PlatformCountryEntity;
import com.wagepayroll.domain.country.PlatformCountryRepository;
import com.wagepayroll.domain.country.PlatformCountryTranslationEntity;
import com.wagepayroll.domain.country.PlatformCountryTranslationRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformCountryService {

	private static final Pattern ISO_ALPHA2 = Pattern.compile("^[A-Z]{2}$");
	private static final Pattern ISO_ALPHA3 = Pattern.compile("^[A-Z]{3}$");
	private static final Pattern ISO_NUMERIC = Pattern.compile("^\\d{1,3}$");
	private static final Pattern DIAL_CODE = Pattern.compile("^\\+[1-9]\\d{0,14}$");
	private static final Set<String> SUPPORTED_READ_LOCALES = Set.of("en", "nl", "nl-sr");
	private static final Set<String> REQUIRED_TRANSLATION_LOCALES = Set.of("en", "nl");
	private static final int MAX_PAGE_SIZE = 100;

	private final PlatformCountryRepository platformCountryRepository;
	private final PlatformCountryTranslationRepository platformCountryTranslationRepository;

	public PlatformCountryService(PlatformCountryRepository platformCountryRepository,
			PlatformCountryTranslationRepository platformCountryTranslationRepository) {
		this.platformCountryRepository = platformCountryRepository;
		this.platformCountryTranslationRepository = platformCountryTranslationRepository;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> listPlatform(int page, int size, String search, Boolean active, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		Set<String> searchLocales = searchLocales(locale);
		Page<PlatformCountryEntity> p = platformCountryRepository.search(active, normalizedSearch(search), searchLocales,
				PageRequest.of(safePage(page), safeSize(size)));
		List<PlatformCountryEntity> rows = p.getContent();
		Map<UUID, List<PlatformCountryTranslationEntity>> translations = loadTranslations(rows);
		List<PlatformCountryDto> items = rows.stream().map(row -> toDto(row, translations.getOrDefault(row.getId(), List.of()), locale, true))
				.toList();
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> listActive(int page, int size, String search, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		Set<String> searchLocales = searchLocales(locale);
		Page<PlatformCountryEntity> p = platformCountryRepository.search(true, normalizedSearch(search), searchLocales,
				PageRequest.of(safePage(page), safeSize(size)));
		List<PlatformCountryEntity> rows = p.getContent();
		Map<UUID, List<PlatformCountryTranslationEntity>> translations = loadTranslations(rows);
		List<PlatformCountryDto> items = rows.stream().map(row -> toDto(row, translations.getOrDefault(row.getId(), List.of()), locale, false))
				.toList();
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}

	@Transactional(readOnly = true)
	public PlatformCountryDto get(UUID id, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformCountryEntity row = platformCountryRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PLATFORM_COUNTRY_NOT_FOUND"));
		List<PlatformCountryTranslationEntity> translations = platformCountryTranslationRepository.findByCountryIdIn(List.of(id));
		return toDto(row, translations, locale, true);
	}

	@Transactional
	public PlatformCountryDto create(PlatformCountryUpsertRequest body, String localeRaw) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		String isoAlpha2 = normalizeIsoAlpha2(body.isoAlpha2());
		String isoAlpha3 = normalizeIsoAlpha3(body.isoAlpha3());
		String isoNumeric = normalizeIsoNumeric(body.isoNumeric());
		String dialCode = normalizeDialCode(body.dialCode());
		Map<String, String> translations = normalizeTranslations(body.translations());
		if (platformCountryRepository.existsByIsoAlpha2IgnoreCase(isoAlpha2)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COUNTRY_ALPHA2_EXISTS");
		}
		if (platformCountryRepository.existsByIsoAlpha3IgnoreCase(isoAlpha3)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COUNTRY_ALPHA3_EXISTS");
		}
		Instant now = Instant.now();
		PlatformCountryEntity row = new PlatformCountryEntity();
		row.setId(UUID.randomUUID());
		row.setIsoAlpha2(isoAlpha2);
		row.setIsoAlpha3(isoAlpha3);
		row.setIsoNumeric(isoNumeric);
		row.setDialCode(dialCode);
		row.setActive(body.active() == null ? true : body.active());
		row.setCreatedAt(now);
		row.setUpdatedAt(now);
		platformCountryRepository.save(row);
		saveTranslations(row.getId(), translations);
		return get(row.getId(), localeRaw);
	}

	@Transactional
	public PlatformCountryDto update(UUID id, PlatformCountryUpsertRequest body, String localeRaw) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		PlatformCountryEntity row = platformCountryRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PLATFORM_COUNTRY_NOT_FOUND"));
		String isoAlpha2 = normalizeIsoAlpha2(body.isoAlpha2());
		String isoAlpha3 = normalizeIsoAlpha3(body.isoAlpha3());
		String isoNumeric = normalizeIsoNumeric(body.isoNumeric());
		String dialCode = normalizeDialCode(body.dialCode());
		Map<String, String> translations = normalizeTranslations(body.translations());
		if (platformCountryRepository.existsByIsoAlpha2IgnoreCaseAndIdNot(isoAlpha2, id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COUNTRY_ALPHA2_EXISTS");
		}
		if (platformCountryRepository.existsByIsoAlpha3IgnoreCaseAndIdNot(isoAlpha3, id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COUNTRY_ALPHA3_EXISTS");
		}
		row.setIsoAlpha2(isoAlpha2);
		row.setIsoAlpha3(isoAlpha3);
		row.setIsoNumeric(isoNumeric);
		row.setDialCode(dialCode);
		row.setActive(body.active() == null ? row.isActive() : body.active());
		row.setUpdatedAt(Instant.now());
		saveTranslations(row.getId(), translations);
		return get(id, localeRaw);
	}

	@Transactional
	public PlatformCountryDto activate(UUID id, String localeRaw) {
		PlatformCountryEntity row = platformCountryRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PLATFORM_COUNTRY_NOT_FOUND"));
		if (row.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COUNTRY_ALREADY_ACTIVE");
		}
		row.setActive(true);
		row.setUpdatedAt(Instant.now());
		return get(id, localeRaw);
	}

	@Transactional
	public PlatformCountryDto deactivate(UUID id, String localeRaw) {
		PlatformCountryEntity row = platformCountryRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PLATFORM_COUNTRY_NOT_FOUND"));
		if (!row.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COUNTRY_ALREADY_INACTIVE");
		}
		row.setActive(false);
		row.setUpdatedAt(Instant.now());
		return get(id, localeRaw);
	}

	private Map<UUID, List<PlatformCountryTranslationEntity>> loadTranslations(List<PlatformCountryEntity> rows) {
		if (rows.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = rows.stream().map(PlatformCountryEntity::getId).toList();
		List<PlatformCountryTranslationEntity> list = platformCountryTranslationRepository.findByCountryIdIn(ids);
		Map<UUID, List<PlatformCountryTranslationEntity>> out = new HashMap<>();
		for (PlatformCountryTranslationEntity t : list) {
			out.computeIfAbsent(t.getCountryId(), ignored -> new ArrayList<>()).add(t);
		}
		return out;
	}

	private PlatformCountryDto toDto(PlatformCountryEntity row, List<PlatformCountryTranslationEntity> translations, String locale,
			boolean includeTranslations) {
		Map<String, String> names = new HashMap<>();
		for (PlatformCountryTranslationEntity translation : translations) {
			names.put(translation.getLocale().toLowerCase(Locale.ROOT), translation.getName());
		}
		String resolvedName = resolveName(locale, names);
		List<PlatformCountryTranslationDto> translationItems = includeTranslations
				? translations.stream().map(t -> new PlatformCountryTranslationDto(t.getLocale(), t.getName())).toList()
				: List.of();
		return new PlatformCountryDto(row.getId(), row.getIsoAlpha2(), row.getIsoAlpha3(), row.getIsoNumeric(), row.getDialCode(),
				row.isActive(), resolvedName, translationItems, DateTimeFormatter.ISO_INSTANT.format(row.getUpdatedAt()));
	}

	private static String resolveName(String locale, Map<String, String> names) {
		String direct = names.get(locale);
		if (direct != null) {
			return direct;
		}
		if ("nl-sr".equals(locale) && names.containsKey("nl")) {
			return names.get("nl");
		}
		if (names.containsKey("en")) {
			return names.get("en");
		}
		return names.values().stream().findFirst().orElse("-");
	}

	private static String normalizeIsoAlpha2(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_ALPHA2_REQUIRED");
		}
		String v = raw.trim().toUpperCase(Locale.ROOT);
		if (!ISO_ALPHA2.matcher(v).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_ALPHA2_INVALID");
		}
		return v;
	}

	private static String normalizeIsoAlpha3(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_ALPHA3_REQUIRED");
		}
		String v = raw.trim().toUpperCase(Locale.ROOT);
		if (!ISO_ALPHA3.matcher(v).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_ALPHA3_INVALID");
		}
		return v;
	}

	private static String normalizeIsoNumeric(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_NUMERIC_REQUIRED");
		}
		String v = raw.trim();
		if (!ISO_NUMERIC.matcher(v).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_NUMERIC_INVALID");
		}
		return v;
	}

	private static String normalizeDialCode(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String v = raw.trim();
		if (!DIAL_CODE.matcher(v).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_DIAL_CODE_INVALID");
		}
		return v;
	}

	private static Map<String, String> normalizeTranslations(List<PlatformCountryTranslationRequest> rows) {
		if (rows == null || rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_TRANSLATIONS_REQUIRED");
		}
		Map<String, String> out = new LinkedHashMap<>();
		Set<String> duplicates = new HashSet<>();
		for (PlatformCountryTranslationRequest row : rows) {
			if (row == null || row.locale() == null || row.locale().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_TRANSLATION_LOCALE_REQUIRED");
			}
			String locale = row.locale().trim().toLowerCase(Locale.ROOT);
			if (!REQUIRED_TRANSLATION_LOCALES.contains(locale)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_TRANSLATION_LOCALE_UNSUPPORTED");
			}
			if (out.containsKey(locale)) {
				duplicates.add(locale);
			}
			if (row.name() == null || row.name().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_TRANSLATION_NAME_REQUIRED");
			}
			String name = row.name().trim();
			if (name.length() > 100) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_TRANSLATION_NAME_INVALID");
			}
			out.put(locale, name);
		}
		if (!duplicates.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_TRANSLATION_LOCALE_DUPLICATE");
		}
		if (!out.keySet().containsAll(REQUIRED_TRANSLATION_LOCALES)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_TRANSLATIONS_REQUIRED");
		}
		return out;
	}

	private void saveTranslations(UUID countryId, Map<String, String> translations) {
		platformCountryTranslationRepository.deleteByCountryId(countryId);
		platformCountryTranslationRepository.flush();
		for (Map.Entry<String, String> e : translations.entrySet()) {
			PlatformCountryTranslationEntity row = new PlatformCountryTranslationEntity();
			row.setId(UUID.randomUUID());
			row.setCountryId(countryId);
			row.setLocale(e.getKey());
			row.setName(e.getValue());
			platformCountryTranslationRepository.save(row);
		}
	}

	private static String normalizeReadLocale(String raw) {
		if (raw == null || raw.isBlank()) {
			return "en";
		}
		String v = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
		if (!SUPPORTED_READ_LOCALES.contains(v)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LOCALE");
		}
		return v;
	}

	private static Set<String> searchLocales(String locale) {
		if ("nl-sr".equals(locale)) {
			return Set.of("nl", "en");
		}
		if ("nl".equals(locale)) {
			return Set.of("nl", "en");
		}
		return Set.of("en", "nl");
	}

	private static String normalizedSearch(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.trim();
	}

	private static int safePage(int value) {
		return Math.max(value, 0);
	}

	private static int safeSize(int value) {
		return Math.min(Math.max(value, 1), MAX_PAGE_SIZE);
	}
}
