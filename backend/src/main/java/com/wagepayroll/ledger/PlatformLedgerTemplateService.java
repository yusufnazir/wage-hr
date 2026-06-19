package com.wagepayroll.ledger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wagepayroll.api.dto.PlatformLedgerTemplateCreateRequest;
import com.wagepayroll.api.dto.PlatformLedgerTemplatePutRequest;
import com.wagepayroll.api.dto.PlatformLedgerTemplateRowDto;
import com.wagepayroll.api.dto.PlatformLedgerTemplateTranslationDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.domain.country.PlatformCountryRepository;
import com.wagepayroll.domain.ledger.PlatformLedgerTemplateEntity;
import com.wagepayroll.domain.ledger.PlatformLedgerTemplateLocaleEntity;
import com.wagepayroll.domain.ledger.PlatformLedgerTemplateLocaleRepository;
import com.wagepayroll.domain.ledger.PlatformLedgerTemplateRepository;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformLedgerTemplateService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> SUPPORTED_READ_LOCALES = Set.of("en", "nl");

	private final PlatformLedgerTemplateRepository repository;
	private final PlatformLedgerTemplateLocaleRepository localeRepository;
	private final PlatformCountryRepository platformCountryRepository;
	private final PlatformWageComponentTemplateRepository wageComponentTemplateRepository;
	private final AuditService auditService;

	public PlatformLedgerTemplateService(PlatformLedgerTemplateRepository repository,
			PlatformLedgerTemplateLocaleRepository localeRepository, PlatformCountryRepository platformCountryRepository,
			PlatformWageComponentTemplateRepository wageComponentTemplateRepository, AuditService auditService) {
		this.repository = repository;
		this.localeRepository = localeRepository;
		this.platformCountryRepository = platformCountryRepository;
		this.wageComponentTemplateRepository = wageComponentTemplateRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(int page, int size, String countryFilter, Boolean activeFilter, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Specification<PlatformLedgerTemplateEntity> spec = (root, q, cb) -> cb.conjunction();
		if (countryFilter != null && !countryFilter.isBlank()) {
			String cc = countryFilter.trim().toUpperCase(Locale.ROOT);
			spec = spec.and((root, q, cb) -> cb.equal(root.get("countryCode"), cc));
		}
		if (activeFilter != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("active"), activeFilter.booleanValue()));
		}
		Page<PlatformLedgerTemplateEntity> p = repository.findAll(spec,
				PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("countryCode"), Sort.Order.asc("code"))));
		List<PlatformLedgerTemplateEntity> rows = p.getContent();
		Map<UUID, List<PlatformLedgerTemplateLocaleEntity>> translations = loadLocales(rows.stream().map(PlatformLedgerTemplateEntity::getId).toList());
		List<PlatformLedgerTemplateRowDto> items = rows.stream().map(e -> toRow(e, translations.getOrDefault(e.getId(), List.of()), locale))
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
	public PlatformLedgerTemplateRowDto get(UUID id, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformLedgerTemplateEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		List<PlatformLedgerTemplateLocaleEntity> loc = localeRepository.findByPlatformLedgerTemplateIdIn(List.of(id));
		return toRow(e, loc, locale);
	}

	@Transactional
	public PlatformLedgerTemplateRowDto create(PlatformLedgerTemplateCreateRequest body, UUID actorId,
			String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		String countryCode = LedgerTemplateValidation.normalizeIso2(body.countryCode(), "countryCode");
		if (!platformCountryRepository.existsActivePayrollEnabledByIsoAlpha2(countryCode)) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "COUNTRY_NOT_PAYROLL_ENABLED");
		}
		String code = LedgerTemplateValidation.requireCode(body.code(), "code").toUpperCase(Locale.ROOT);
		Map<String, String> translationMap = LedgerTemplateValidation.normalizeLedgerDescriptionTranslations(body.translations());
		boolean active = body.active() == null || body.active().booleanValue();
		if (repository.existsByCountryCodeAndCodeIgnoreCase(countryCode, code)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_LEDGER_CODE");
		}
		Instant now = Instant.now();
		PlatformLedgerTemplateEntity e = new PlatformLedgerTemplateEntity();
		e.setId(UUID.randomUUID());
		e.setCountryCode(countryCode);
		e.setCode(code);
		e.setActive(active);
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		try {
			repository.save(e);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_LEDGER_CODE");
		}
		saveLocales(e.getId(), translationMap);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_LEDGER_TEMPLATE_CREATED,
				AuditResourceTypes.PLATFORM_LEDGER_TEMPLATE, e.getId().toString(), correlationId,
				Map.of("countryCode", countryCode, "code", code));
		List<PlatformLedgerTemplateLocaleEntity> loc = localeRepository.findByPlatformLedgerTemplateIdIn(List.of(e.getId()));
		return toRow(e, loc, locale);
	}

	@Transactional
	public PlatformLedgerTemplateRowDto update(UUID id, PlatformLedgerTemplatePutRequest body, UUID actorId,
			String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		if (body.active() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "active is required");
		}
		PlatformLedgerTemplateEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		List<PlatformLedgerTemplateLocaleEntity> beforeLocales = localeRepository.findByPlatformLedgerTemplateIdIn(List.of(id));
		Map<String, Object> before = snapshot(e, beforeLocales);
		String countryCode = LedgerTemplateValidation.normalizeIso2(body.countryCode(), "countryCode");
		if (!platformCountryRepository.existsActivePayrollEnabledByIsoAlpha2(countryCode)) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "COUNTRY_NOT_PAYROLL_ENABLED");
		}
		String code = LedgerTemplateValidation.requireCode(body.code(), "code").toUpperCase(Locale.ROOT);
		Map<String, String> translationMap = LedgerTemplateValidation.normalizeLedgerDescriptionTranslations(body.translations());
		if (repository.existsByCountryCodeAndCodeIgnoreCaseAndIdNot(countryCode, code, id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_LEDGER_CODE");
		}
		e.setCountryCode(countryCode);
		e.setCode(code);
		e.setActive(body.active().booleanValue());
		e.setUpdatedAt(Instant.now());
		try {
			repository.save(e);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_LEDGER_CODE");
		}
		saveLocales(id, translationMap);
		List<PlatformLedgerTemplateLocaleEntity> afterLocales = localeRepository.findByPlatformLedgerTemplateIdIn(List.of(id));
		Map<String, Object> after = snapshot(e, afterLocales);
		Map<String, Object> changes = diff(before, after);
		if (!changes.isEmpty()) {
			Map<String, Object> meta = new LinkedHashMap<>();
			meta.put("id", id.toString());
			meta.put("changes", changes);
			auditService.append(null, actorId, AuditActionCodes.PLATFORM_LEDGER_TEMPLATE_UPDATED,
					AuditResourceTypes.PLATFORM_LEDGER_TEMPLATE, id.toString(), correlationId, meta);
		}
		return toRow(e, afterLocales, locale);
	}

	@Transactional
	public PlatformLedgerTemplateRowDto activate(UUID id, UUID actorId, String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformLedgerTemplateEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (e.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "LEDGER_TEMPLATE_ALREADY_ACTIVE");
		}
		e.setActive(true);
		e.setUpdatedAt(Instant.now());
		repository.save(e);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_LEDGER_TEMPLATE_ACTIVATED,
				AuditResourceTypes.PLATFORM_LEDGER_TEMPLATE, id.toString(), correlationId,
				Map.of("id", id.toString(), "countryCode", e.getCountryCode(), "code", e.getCode()));
		List<PlatformLedgerTemplateLocaleEntity> loc = localeRepository.findByPlatformLedgerTemplateIdIn(List.of(id));
		return toRow(e, loc, locale);
	}

	@Transactional
	public PlatformLedgerTemplateRowDto deactivate(UUID id, UUID actorId, String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformLedgerTemplateEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!e.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "LEDGER_TEMPLATE_ALREADY_INACTIVE");
		}
		if (wageComponentTemplateRepository.countLinkedToLedgerTemplate(id) > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "LEDGER_TEMPLATE_IN_USE");
		}
		e.setActive(false);
		e.setUpdatedAt(Instant.now());
		repository.save(e);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_LEDGER_TEMPLATE_DEACTIVATED,
				AuditResourceTypes.PLATFORM_LEDGER_TEMPLATE, id.toString(), correlationId,
				Map.of("id", id.toString(), "countryCode", e.getCountryCode(), "code", e.getCode()));
		List<PlatformLedgerTemplateLocaleEntity> loc = localeRepository.findByPlatformLedgerTemplateIdIn(List.of(id));
		return toRow(e, loc, locale);
	}

	private Map<UUID, List<PlatformLedgerTemplateLocaleEntity>> loadLocales(Collection<UUID> templateIds) {
		if (templateIds == null || templateIds.isEmpty()) {
			return Map.of();
		}
		List<PlatformLedgerTemplateLocaleEntity> all = localeRepository.findByPlatformLedgerTemplateIdIn(templateIds);
		return all.stream().collect(Collectors.groupingBy(PlatformLedgerTemplateLocaleEntity::getPlatformLedgerTemplateId));
	}

	private void saveLocales(UUID templateId, Map<String, String> translations) {
		localeRepository.deleteByPlatformLedgerTemplateId(templateId);
		localeRepository.flush();
		for (Map.Entry<String, String> entry : translations.entrySet()) {
			PlatformLedgerTemplateLocaleEntity row = new PlatformLedgerTemplateLocaleEntity();
			row.setId(UUID.randomUUID());
			row.setPlatformLedgerTemplateId(templateId);
			row.setLocale(entry.getKey());
			row.setDescription(entry.getValue());
			localeRepository.save(row);
		}
	}

	private static Map<String, Object> snapshot(PlatformLedgerTemplateEntity e,
			List<PlatformLedgerTemplateLocaleEntity> locales) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("countryCode", e.getCountryCode());
		m.put("code", e.getCode());
		Map<String, String> desc = new LinkedHashMap<>();
		for (PlatformLedgerTemplateLocaleEntity l : locales) {
			desc.put(l.getLocale(), l.getDescription());
		}
		m.put("descriptions", desc);
		m.put("active", e.isActive());
		return m;
	}

	private static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (String k : before.keySet()) {
			Object o0 = before.get(k);
			Object o1 = after.get(k);
			if (o0 == null ? o1 != null : !o0.equals(o1)) {
				Map<String, Object> pair = new LinkedHashMap<>();
				pair.put("old", o0);
				pair.put("new", o1);
				out.put(k, pair);
			}
		}
		return out;
	}

	private PlatformLedgerTemplateRowDto toRow(PlatformLedgerTemplateEntity e,
			List<PlatformLedgerTemplateLocaleEntity> localeRows, String locale) {
		Map<String, String> byLocale = new HashMap<>();
		for (PlatformLedgerTemplateLocaleEntity row : localeRows) {
			byLocale.put(row.getLocale().toLowerCase(Locale.ROOT), row.getDescription());
		}
		String resolved = resolveDescription(locale, byLocale);
		List<PlatformLedgerTemplateTranslationDto> translationDtos = localeRows.stream()
				.sorted((a, b) -> a.getLocale().compareToIgnoreCase(b.getLocale()))
				.map(r -> new PlatformLedgerTemplateTranslationDto(r.getLocale(), r.getDescription()))
				.toList();
		return new PlatformLedgerTemplateRowDto(e.getId(), e.getCountryCode(), e.getCode(), resolved, translationDtos, e.isActive(),
				e.getCreatedAt(), e.getUpdatedAt());
	}

	private static String resolveDescription(String locale, Map<String, String> descriptions) {
		String direct = descriptions.get(locale);
		if (direct != null) {
			return direct;
		}
		if (descriptions.containsKey("en")) {
			return descriptions.get("en");
		}
		return descriptions.values().stream().findFirst().orElse("-");
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
}
