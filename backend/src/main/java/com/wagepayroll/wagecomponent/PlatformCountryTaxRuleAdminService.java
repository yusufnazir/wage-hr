package com.wagepayroll.wagecomponent;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.api.dto.PlatformCountryTaxRuleCreateRequest;
import com.wagepayroll.api.dto.PlatformCountryTaxRulePutRequest;
import com.wagepayroll.api.dto.PlatformCountryTaxRuleRowDto;
import com.wagepayroll.domain.country.PlatformCountryRepository;
import com.wagepayroll.domain.wagecomponent.PlatformCountryTaxRuleEntity;
import com.wagepayroll.domain.wagecomponent.PlatformCountryTaxRuleRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformCountryTaxRuleAdminService {

	private static final int MAX_PAGE_SIZE = 100;

	private static final int MAX_RULE_CODE_LEN = 64;

	private static final int MAX_NAME_LEN = 200;

	private static final int MAX_PARAMETERS_JSON_LEN = 4000;

	private final PlatformCountryTaxRuleRepository repository;

	private final PlatformCountryRepository platformCountryRepository;

	private final ObjectMapper objectMapper;

	public PlatformCountryTaxRuleAdminService(PlatformCountryTaxRuleRepository repository,
			PlatformCountryRepository platformCountryRepository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.platformCountryRepository = platformCountryRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(int page, int size, String countryFilter, Boolean activeFilter, String search) {
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Specification<PlatformCountryTaxRuleEntity> spec = (root, q, cb) -> cb.conjunction();
		if (countryFilter != null && !countryFilter.isBlank()) {
			String cc = countryFilter.trim().toUpperCase(Locale.ROOT);
			if (cc.length() != 2) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_COUNTRY_CODE");
			}
			spec = spec.and((root, q, cb) -> cb.equal(root.get("countryCode"), cc));
		}
		if (activeFilter != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("active"), activeFilter.booleanValue()));
		}
		if (search != null && !search.isBlank()) {
			String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
			spec = spec.and((root, q, cb) -> cb.or(
					cb.like(cb.lower(root.get("ruleCode")), term),
					cb.like(cb.lower(root.get("name")), term)));
		}
		Page<PlatformCountryTaxRuleEntity> p = repository.findAll(spec,
				PageRequest.of(safePage, safeSize,
						Sort.by(Sort.Order.asc("countryCode"), Sort.Order.desc("effectiveFrom"), Sort.Order.asc("ruleCode"))));
		List<PlatformCountryTaxRuleRowDto> items = p.getContent().stream().map(this::toRow).toList();
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}

	@Transactional(readOnly = true)
	public PlatformCountryTaxRuleRowDto get(UUID id) {
		return toRow(repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
	}

	@Transactional
	public PlatformCountryTaxRuleRowDto create(PlatformCountryTaxRuleCreateRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		String countryCode = normalizeCountryCode(body.countryCode());
		requirePayrollEnabledCountry(countryCode);
		String ruleCode = normalizeRuleCode(body.ruleCode());
		String name = requireName(body.name());
		var effectiveFrom = requireNonNull(body.effectiveFrom(), "effectiveFrom");
		var effectiveTo = body.effectiveTo();
		validateEffectiveWindow(effectiveFrom, effectiveTo);
		if (repository.existsByCountryCodeAndRuleCodeAndEffectiveFrom(countryCode, ruleCode, effectiveFrom)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "PLATFORM_COUNTRY_TAX_RULE_VERSION_EXISTS");
		}
		String parametersJson = normalizeParametersJson(body.parametersJson());
		boolean active = body.active() == null || body.active().booleanValue();
		Instant now = Instant.now();
		PlatformCountryTaxRuleEntity e = new PlatformCountryTaxRuleEntity();
		e.setId(UUID.randomUUID());
		e.setCountryCode(countryCode);
		e.setRuleCode(ruleCode);
		e.setName(name);
		e.setEffectiveFrom(effectiveFrom);
		e.setEffectiveTo(effectiveTo);
		e.setParametersJson(parametersJson);
		e.setActive(active);
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		return toRow(repository.save(e));
	}

	@Transactional
	public PlatformCountryTaxRuleRowDto update(UUID id, PlatformCountryTaxRulePutRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		PlatformCountryTaxRuleEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		String name = requireName(body.name());
		String parametersJson = normalizeParametersJson(body.parametersJson());
		var effectiveTo = body.effectiveTo();
		validateEffectiveWindow(e.getEffectiveFrom(), effectiveTo);
		Boolean activeIn = body.active();
		boolean active = activeIn != null ? activeIn.booleanValue() : e.isActive();
		e.setName(name);
		e.setParametersJson(parametersJson);
		e.setEffectiveTo(effectiveTo);
		e.setActive(active);
		e.setUpdatedAt(Instant.now());
		return toRow(repository.save(e));
	}

	@Transactional
	public PlatformCountryTaxRuleRowDto activate(UUID id) {
		PlatformCountryTaxRuleEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		e.setActive(true);
		e.setUpdatedAt(Instant.now());
		return toRow(repository.save(e));
	}

	@Transactional
	public PlatformCountryTaxRuleRowDto deactivate(UUID id) {
		PlatformCountryTaxRuleEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		e.setActive(false);
		e.setUpdatedAt(Instant.now());
		return toRow(repository.save(e));
	}

	private void requirePayrollEnabledCountry(String countryCode) {
		if (!platformCountryRepository.existsActivePayrollEnabledByIsoAlpha2(countryCode)) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "COUNTRY_NOT_PAYROLL_ENABLED");
		}
	}

	private static String normalizeCountryCode(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_CODE_REQUIRED");
		}
		String cc = raw.trim().toUpperCase(Locale.ROOT);
		if (cc.length() != 2) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_COUNTRY_CODE");
		}
		return cc;
	}

	private static String normalizeRuleCode(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RULE_CODE_REQUIRED");
		}
		String s = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
		if (s.length() > MAX_RULE_CODE_LEN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RULE_CODE_TOO_LONG");
		}
		if (!s.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RULE_CODE_INVALID");
		}
		return s;
	}

	private static String requireName(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED");
		}
		String n = raw.trim();
		if (n.length() > MAX_NAME_LEN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NAME_TOO_LONG");
		}
		return n;
	}

	private static <T> T requireNonNull(T v, String field) {
		if (v == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field.toUpperCase(Locale.ROOT) + "_REQUIRED");
		}
		return v;
	}

	private static void validateEffectiveWindow(java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo) {
		if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EFFECTIVE_TO_BEFORE_FROM");
		}
	}

	private String normalizeParametersJson(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARAMETERS_JSON_REQUIRED");
		}
		JsonNode node;
		try {
			node = objectMapper.readTree(raw);
		}
		catch (JsonProcessingException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARAMETERS_JSON_INVALID");
		}
		if (!node.isObject() && !node.isArray()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARAMETERS_JSON_MUST_BE_OBJECT_OR_ARRAY");
		}
		String compact;
		try {
			compact = objectMapper.writeValueAsString(node);
		}
		catch (JsonProcessingException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARAMETERS_JSON_INVALID");
		}
		if (compact.length() > MAX_PARAMETERS_JSON_LEN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARAMETERS_JSON_TOO_LONG");
		}
		return compact;
	}

	private PlatformCountryTaxRuleRowDto toRow(PlatformCountryTaxRuleEntity e) {
		return new PlatformCountryTaxRuleRowDto(e.getId(), e.getCountryCode(), e.getRuleCode(), e.getName(), e.getEffectiveFrom(),
				e.getEffectiveTo(), e.getParametersJson(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
	}
}
