package com.wagepayroll.banktemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantBankTemplateCreateRequest;
import com.wagepayroll.api.dto.TenantBankTemplatePutRequest;
import com.wagepayroll.api.dto.TenantBankTemplateRowDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.domain.banktemplate.TenantBankTemplateEntity;
import com.wagepayroll.domain.banktemplate.TenantBankTemplateRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantBankTemplateService {

	private static final int MAX_PAGE_SIZE = 100;

	private final TenantBankTemplateRepository repository;
	private final TenantCompanyRepository companyRepository;
	private final AuditService auditService;

	public TenantBankTemplateService(TenantBankTemplateRepository repository, TenantCompanyRepository companyRepository,
			AuditService auditService) {
		this.repository = repository;
		this.companyRepository = companyRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(UUID tenantId, UUID companyId, int page, int size, Boolean activeFilter) {
		if (companyId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyId is required");
		}
		requireCompanyEntity(tenantId, companyId);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Page<TenantBankTemplateEntity> p = activeFilter == null
				? repository.findByTenantIdAndCompanyId(tenantId, companyId,
						PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("name"))))
				: repository.findByTenantIdAndCompanyIdAndActive(tenantId, companyId, activeFilter.booleanValue(),
						PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("name"))));
		List<TenantBankTemplateRowDto> items = p.getContent().stream().map(this::toRow).toList();
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}

	@Transactional(readOnly = true)
	public TenantBankTemplateRowDto get(UUID tenantId, UUID id) {
		TenantBankTemplateEntity e = repository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return toRow(e);
	}

	@Transactional
	public TenantBankTemplateRowDto create(UUID tenantId, TenantBankTemplateCreateRequest body, UUID actorId,
			String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		if (body.companyId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyId is required");
		}
		TenantCompanyEntity company = requireCompanyEntity(tenantId, body.companyId());
		Instant now = Instant.now();
		TenantBankTemplateEntity e = new TenantBankTemplateEntity();
		e.setId(UUID.randomUUID());
		e.setTenantId(tenantId);
		e.setCompanyId(body.companyId());
		e.setPlatformBankTemplateId(null);
		e.setCountryCode(company.getPayrollCountry());
		e.setName(BankTemplateValidation.requireName(body.name(), "name"));
		e.setBankName(BankTemplateValidation.trimBankName(body.bankName()));
		e.setSwiftBic(BankTemplateValidation.normalizeSwiftBicOrNull(body.swiftBic()));
		e.setBankCode(BankTemplateValidation.trimBankCode(body.bankCode()));
		e.setAccountNumberFormat(BankTemplateValidation.trimAccountFormat(body.accountNumberFormat()));
		e.setCurrencyCode(BankTemplateValidation.normalizeCurrencyOrNull(body.currencyCode()));
		e.setActive(body.active() == null || body.active().booleanValue());
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		repository.save(e);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_BANK_TEMPLATE_CREATED,
				AuditResourceTypes.TENANT_BANK_TEMPLATE, e.getId().toString(), correlationId,
				Map.of("id", e.getId().toString(), "companyId", e.getCompanyId().toString(), "countryCode", e.getCountryCode(),
						"name", e.getName()));
		return toRow(e);
	}

	@Transactional
	public TenantBankTemplateRowDto update(UUID tenantId, UUID id, TenantBankTemplatePutRequest body, UUID actorId,
			String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		if (body.active() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "active is required");
		}
		TenantBankTemplateEntity e = repository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		requireCompanyEntity(tenantId, e.getCompanyId());
		Map<String, Object> before = snapshot(e);
		e.setName(BankTemplateValidation.requireName(body.name(), "name"));
		e.setBankName(BankTemplateValidation.trimBankName(body.bankName()));
		e.setSwiftBic(BankTemplateValidation.normalizeSwiftBicOrNull(body.swiftBic()));
		e.setBankCode(BankTemplateValidation.trimBankCode(body.bankCode()));
		e.setAccountNumberFormat(BankTemplateValidation.trimAccountFormat(body.accountNumberFormat()));
		e.setCurrencyCode(BankTemplateValidation.normalizeCurrencyOrNull(body.currencyCode()));
		e.setActive(body.active().booleanValue());
		e.setUpdatedAt(Instant.now());
		repository.save(e);
		Map<String, Object> after = snapshot(e);
		Map<String, Object> changes = diff(before, after);
		if (!changes.isEmpty()) {
			Map<String, Object> meta = new LinkedHashMap<>();
			meta.put("id", id.toString());
			meta.put("companyId", e.getCompanyId().toString());
			meta.put("changes", changes);
			auditService.append(tenantId, actorId, AuditActionCodes.TENANT_BANK_TEMPLATE_UPDATED,
					AuditResourceTypes.TENANT_BANK_TEMPLATE, id.toString(), correlationId, meta);
		}
		return toRow(e);
	}

	@Transactional
	public TenantBankTemplateRowDto activate(UUID tenantId, UUID id, UUID actorId, String correlationId) {
		TenantBankTemplateEntity e = repository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		requireCompanyEntity(tenantId, e.getCompanyId());
		if (e.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "BANK_TEMPLATE_ALREADY_ACTIVE");
		}
		e.setActive(true);
		e.setUpdatedAt(Instant.now());
		repository.save(e);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_BANK_TEMPLATE_ACTIVATED,
				AuditResourceTypes.TENANT_BANK_TEMPLATE, id.toString(), correlationId,
				Map.of("id", id.toString(), "companyId", e.getCompanyId().toString(), "countryCode", e.getCountryCode()));
		return toRow(e);
	}

	@Transactional
	public TenantBankTemplateRowDto deactivate(UUID tenantId, UUID id, UUID actorId, String correlationId) {
		TenantBankTemplateEntity e = repository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		requireCompanyEntity(tenantId, e.getCompanyId());
		if (!e.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "BANK_TEMPLATE_ALREADY_INACTIVE");
		}
		e.setActive(false);
		e.setUpdatedAt(Instant.now());
		repository.save(e);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_BANK_TEMPLATE_DEACTIVATED,
				AuditResourceTypes.TENANT_BANK_TEMPLATE, id.toString(), correlationId,
				Map.of("id", id.toString(), "companyId", e.getCompanyId().toString(), "countryCode", e.getCountryCode()));
		return toRow(e);
	}

	@Transactional
	public TenantBankTemplateRowDto delete(UUID tenantId, UUID id, UUID actorId, String correlationId) {
		TenantBankTemplateEntity e = repository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		requireCompanyEntity(tenantId, e.getCompanyId());
		repository.delete(e);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_BANK_TEMPLATE_DELETED,
				AuditResourceTypes.TENANT_BANK_TEMPLATE, id.toString(), correlationId,
				Map.of("id", id.toString(), "companyId", e.getCompanyId().toString(), "countryCode", e.getCountryCode(),
						"name", e.getName()));
		return toRow(e);
	}

	private TenantCompanyEntity requireCompanyEntity(UUID tenantId, UUID companyId) {
		return companyRepository.findByIdAndTenantId(companyId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private TenantBankTemplateRowDto toRow(TenantBankTemplateEntity e) {
		return new TenantBankTemplateRowDto(e.getId(), e.getCompanyId(), e.getPlatformBankTemplateId(), e.getCountryCode(),
				e.getName(), e.getBankName(), e.getSwiftBic(), e.getBankCode(), e.getAccountNumberFormat(),
				e.getCurrencyCode(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
	}

	private static Map<String, Object> snapshot(TenantBankTemplateEntity e) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", e.getName());
		m.put("bankName", e.getBankName());
		m.put("swiftBic", e.getSwiftBic());
		m.put("bankCode", e.getBankCode());
		m.put("accountNumberFormat", e.getAccountNumberFormat());
		m.put("currencyCode", e.getCurrencyCode());
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
}
