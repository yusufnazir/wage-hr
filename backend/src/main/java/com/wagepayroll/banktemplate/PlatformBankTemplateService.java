package com.wagepayroll.banktemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.api.dto.PlatformBankTemplateCreateRequest;
import com.wagepayroll.api.dto.PlatformBankTemplatePutRequest;
import com.wagepayroll.api.dto.PlatformBankTemplateRowDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.domain.banktemplate.PlatformBankTemplateEntity;
import com.wagepayroll.domain.banktemplate.PlatformBankTemplateRepository;
import com.wagepayroll.domain.country.PlatformCountryRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformBankTemplateService {

	private static final int MAX_PAGE_SIZE = 100;

	private final PlatformBankTemplateRepository repository;
	private final PlatformCountryRepository platformCountryRepository;
	private final AuditService auditService;

	public PlatformBankTemplateService(PlatformBankTemplateRepository repository,
			PlatformCountryRepository platformCountryRepository, AuditService auditService) {
		this.repository = repository;
		this.platformCountryRepository = platformCountryRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(int page, int size, String countryFilter, Boolean activeFilter) {
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Specification<PlatformBankTemplateEntity> spec = (root, q, cb) -> cb.conjunction();
		if (countryFilter != null && !countryFilter.isBlank()) {
			String cc = countryFilter.trim().toUpperCase(Locale.ROOT);
			spec = spec.and((root, q, cb) -> cb.equal(root.get("countryCode"), cc));
		}
		if (activeFilter != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("active"), activeFilter.booleanValue()));
		}
		Page<PlatformBankTemplateEntity> p = repository.findAll(spec,
				PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("countryCode"), Sort.Order.asc("name"))));
		List<PlatformBankTemplateRowDto> items = p.getContent().stream().map(this::toRow).toList();
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}

	@Transactional(readOnly = true)
	public PlatformBankTemplateRowDto get(UUID id) {
		return toRow(repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
	}

	@Transactional
	public PlatformBankTemplateRowDto create(PlatformBankTemplateCreateRequest body, UUID actorId, String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		String countryCode = BankTemplateValidation.normalizeIso2(body.countryCode(), "countryCode");
		if (!platformCountryRepository.existsActivePayrollEnabledByIsoAlpha2(countryCode)) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "COUNTRY_NOT_PAYROLL_ENABLED");
		}
		String name = BankTemplateValidation.requireName(body.name(), "name");
		String bankName = BankTemplateValidation.trimBankName(body.bankName());
		String swiftBic = BankTemplateValidation.normalizeSwiftBicOrNull(body.swiftBic());
		String bankCode = BankTemplateValidation.trimBankCode(body.bankCode());
		String accountNumberFormat = BankTemplateValidation.trimAccountFormat(body.accountNumberFormat());
		boolean active = body.active() == null || body.active().booleanValue();
		Instant now = Instant.now();
		PlatformBankTemplateEntity e = new PlatformBankTemplateEntity();
		e.setId(UUID.randomUUID());
		e.setCountryCode(countryCode);
		e.setName(name);
		e.setBankName(bankName);
		e.setSwiftBic(swiftBic);
		e.setBankCode(bankCode);
		e.setAccountNumberFormat(accountNumberFormat);
		e.setActive(active);
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		repository.save(e);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_BANK_TEMPLATE_CREATED,
				AuditResourceTypes.PLATFORM_BANK_TEMPLATE, e.getId().toString(), correlationId,
				Map.of("countryCode", countryCode, "name", name));
		return toRow(e);
	}

	@Transactional
	public PlatformBankTemplateRowDto update(UUID id, PlatformBankTemplatePutRequest body, UUID actorId,
			String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		if (body.active() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "active is required");
		}
		PlatformBankTemplateEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		Map<String, Object> before = snapshot(e);
		String name = BankTemplateValidation.requireName(body.name(), "name");
		String bankName = BankTemplateValidation.trimBankName(body.bankName());
		String swiftBic = BankTemplateValidation.normalizeSwiftBicOrNull(body.swiftBic());
		String bankCode = BankTemplateValidation.trimBankCode(body.bankCode());
		String accountNumberFormat = BankTemplateValidation.trimAccountFormat(body.accountNumberFormat());
		e.setName(name);
		e.setBankName(bankName);
		e.setSwiftBic(swiftBic);
		e.setBankCode(bankCode);
		e.setAccountNumberFormat(accountNumberFormat);
		e.setActive(body.active().booleanValue());
		e.setUpdatedAt(Instant.now());
		repository.save(e);
		Map<String, Object> after = snapshot(e);
		Map<String, Object> changes = diff(before, after);
		if (!changes.isEmpty()) {
			Map<String, Object> meta = new LinkedHashMap<>();
			meta.put("id", id.toString());
			meta.put("changes", changes);
			auditService.append(null, actorId, AuditActionCodes.PLATFORM_BANK_TEMPLATE_UPDATED,
					AuditResourceTypes.PLATFORM_BANK_TEMPLATE, id.toString(), correlationId, meta);
		}
		return toRow(e);
	}

	@Transactional
	public PlatformBankTemplateRowDto activate(UUID id, UUID actorId, String correlationId) {
		PlatformBankTemplateEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (e.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "BANK_TEMPLATE_ALREADY_ACTIVE");
		}
		e.setActive(true);
		e.setUpdatedAt(Instant.now());
		repository.save(e);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_BANK_TEMPLATE_ACTIVATED,
				AuditResourceTypes.PLATFORM_BANK_TEMPLATE, id.toString(), correlationId,
				Map.of("id", id.toString(), "countryCode", e.getCountryCode(), "name", e.getName()));
		return toRow(e);
	}

	@Transactional
	public PlatformBankTemplateRowDto deactivate(UUID id, UUID actorId, String correlationId) {
		PlatformBankTemplateEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!e.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "BANK_TEMPLATE_ALREADY_INACTIVE");
		}
		e.setActive(false);
		e.setUpdatedAt(Instant.now());
		repository.save(e);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_BANK_TEMPLATE_DEACTIVATED,
				AuditResourceTypes.PLATFORM_BANK_TEMPLATE, id.toString(), correlationId,
				Map.of("id", id.toString(), "countryCode", e.getCountryCode(), "name", e.getName()));
		return toRow(e);
	}

	private static Map<String, Object> snapshot(PlatformBankTemplateEntity e) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", e.getName());
		m.put("bankName", e.getBankName());
		m.put("swiftBic", e.getSwiftBic());
		m.put("bankCode", e.getBankCode());
		m.put("accountNumberFormat", e.getAccountNumberFormat());
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

	private PlatformBankTemplateRowDto toRow(PlatformBankTemplateEntity e) {
		return new PlatformBankTemplateRowDto(e.getId(), e.getCountryCode(), e.getName(), e.getBankName(), e.getSwiftBic(),
				e.getBankCode(), e.getAccountNumberFormat(), e.isActive(), e.getCreatedAt(),
				e.getUpdatedAt());
	}
}
