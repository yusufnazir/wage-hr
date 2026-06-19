package com.wagepayroll.paymentlocation;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.wagepayroll.api.dto.TenantPaymentLocationCreateRequest;
import com.wagepayroll.api.dto.TenantPaymentLocationRowDto;
import com.wagepayroll.api.dto.TenantPaymentLocationUpdateRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.domain.banktemplate.TenantBankTemplateEntity;
import com.wagepayroll.domain.banktemplate.TenantBankTemplateRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.paymentlocation.TenantPaymentLocationEntity;
import com.wagepayroll.domain.paymentlocation.TenantPaymentLocationRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantPaymentLocationService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Pattern ISO_CURRENCY = Pattern.compile("^[A-Z]{3}$");
	private static final String TYPE_CASH = "CASH";
	private static final String TYPE_BANK_ACCOUNT = "BANK_ACCOUNT";

	private final TenantPaymentLocationRepository repository;
	private final TenantCompanyRepository companyRepository;
	private final TenantBankTemplateRepository bankTemplateRepository;
	private final AuditService auditService;

	public TenantPaymentLocationService(TenantPaymentLocationRepository repository,
			TenantCompanyRepository companyRepository, TenantBankTemplateRepository bankTemplateRepository,
			AuditService auditService) {
		this.repository = repository;
		this.companyRepository = companyRepository;
		this.bankTemplateRepository = bankTemplateRepository;
		this.auditService = auditService;
	}

	// ─── List ─────────────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public Map<String, Object> list(UUID tenantId, UUID companyId, int page, int size, Boolean activeFilter) {
		if (companyId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyId is required");
		}
		requireCompany(tenantId, companyId);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Page<TenantPaymentLocationEntity> p = activeFilter == null
				? repository.findByTenantIdAndCompanyId(tenantId, companyId,
						PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("name"))))
				: repository.findByTenantIdAndCompanyIdAndActive(tenantId, companyId, activeFilter.booleanValue(),
						PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("name"))));
		List<TenantPaymentLocationRowDto> items = p.getContent().stream().map(e -> toRow(e, false)).toList();
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}

	// ─── Get ──────────────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public TenantPaymentLocationRowDto get(UUID tenantId, UUID id) {
		TenantPaymentLocationEntity e = requireLocation(tenantId, id);
		return toRow(e, true);
	}

	// ─── Create ───────────────────────────────────────────────────────────────

	@Transactional
	public TenantPaymentLocationRowDto create(UUID tenantId, TenantPaymentLocationCreateRequest body, UUID actorId,
			String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		if (body.companyId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyId is required");
		}
		requireCompany(tenantId, body.companyId());

		String paymentType = requirePaymentType(body.paymentType());
		String name = requireName(body.name());
		String currency = requireCurrency(body.currency());

		// Uniqueness check (BR-1)
		if (repository.existsByTenantIdAndCompanyIdAndNameIgnoreCase(tenantId, body.companyId(), name)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "PAYMENT_LOCATION_NAME_DUPLICATE");
		}

		TenantBankTemplateEntity bankTemplate = null;
		String accountNumber = null;

		if (TYPE_BANK_ACCOUNT.equals(paymentType)) {
			// BR-5: both required
			if (body.bankTemplateId() == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bankTemplateId is required for BANK_ACCOUNT");
			}
			if (body.accountNumber() == null || body.accountNumber().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountNumber is required for BANK_ACCOUNT");
			}
			bankTemplate = requireActiveBankTemplate(tenantId, body.companyId(), body.bankTemplateId());
			accountNumber = normalizeAccountNumber(body.accountNumber());
			validateAccountNumber(accountNumber, bankTemplate.getAccountNumberFormat());
		} else {
			// BR-4: CASH must have null bank fields
			if (body.bankTemplateId() != null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"bankTemplateId must be null for CASH payment type");
			}
			if (body.accountNumber() != null && !body.accountNumber().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"accountNumber must be null for CASH payment type");
			}
		}

		Instant now = Instant.now();
		TenantPaymentLocationEntity e = new TenantPaymentLocationEntity();
		e.setId(UUID.randomUUID());
		e.setTenantId(tenantId);
		e.setCompanyId(body.companyId());
		e.setName(name);
		e.setPaymentType(paymentType);
		e.setCurrency(currency);
		e.setBankTemplateId(bankTemplate != null ? bankTemplate.getId() : null);
		e.setAccountNumber(accountNumber);
		e.setActive(true);
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		repository.save(e);

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("id", e.getId().toString());
		meta.put("companyId", e.getCompanyId().toString());
		meta.put("paymentType", paymentType);
		meta.put("name", name);
		auditService.append(tenantId, actorId, AuditActionCodes.PAYMENT_LOCATION_CREATED,
				AuditResourceTypes.TENANT_PAYMENT_LOCATION, e.getId().toString(), correlationId, meta);

		return toRow(e, true);
	}

	// ─── Update ───────────────────────────────────────────────────────────────

	@Transactional
	public TenantPaymentLocationRowDto update(UUID tenantId, UUID id, TenantPaymentLocationUpdateRequest body,
			UUID actorId, String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		TenantPaymentLocationEntity e = requireLocation(tenantId, id);
		String paymentType = e.getPaymentType();

		String name = requireName(body.name());
		String currency = requireCurrency(body.currency());

		// Uniqueness check (BR-1) — exclude self
		if (repository.existsByTenantIdAndCompanyIdAndNameIgnoreCaseAndIdNot(tenantId, e.getCompanyId(), name, id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "PAYMENT_LOCATION_NAME_DUPLICATE");
		}

		Map<String, Object> before = snapshot(e);

		TenantBankTemplateEntity bankTemplate = null;
		String accountNumber = null;

		if (TYPE_BANK_ACCOUNT.equals(paymentType)) {
			if (body.bankTemplateId() == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bankTemplateId is required for BANK_ACCOUNT");
			}
			if (body.accountNumber() == null || body.accountNumber().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountNumber is required for BANK_ACCOUNT");
			}
			bankTemplate = requireActiveBankTemplate(tenantId, e.getCompanyId(), body.bankTemplateId());
			accountNumber = normalizeAccountNumber(body.accountNumber());
			validateAccountNumber(accountNumber, bankTemplate.getAccountNumberFormat());
			e.setBankTemplateId(bankTemplate.getId());
			e.setAccountNumber(accountNumber);
		} else {
			// CASH: enforce null bank fields
			if (body.bankTemplateId() != null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"bankTemplateId must be null for CASH payment type");
			}
			if (body.accountNumber() != null && !body.accountNumber().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"accountNumber must be null for CASH payment type");
			}
			e.setBankTemplateId(null);
			e.setAccountNumber(null);
		}

		e.setName(name);
		e.setCurrency(currency);
		e.setUpdatedAt(Instant.now());
		repository.save(e);

		Map<String, Object> after = snapshot(e);
		Map<String, Object> changes = diff(before, after);
		if (!changes.isEmpty()) {
			Map<String, Object> meta = new LinkedHashMap<>();
			meta.put("id", id.toString());
			meta.put("companyId", e.getCompanyId().toString());
			meta.put("changes", changes);
			auditService.append(tenantId, actorId, AuditActionCodes.PAYMENT_LOCATION_UPDATED,
					AuditResourceTypes.TENANT_PAYMENT_LOCATION, id.toString(), correlationId, meta);
		}
		return toRow(e, true);
	}

	// ─── Activate ─────────────────────────────────────────────────────────────

	@Transactional
	public TenantPaymentLocationRowDto activate(UUID tenantId, UUID id, UUID actorId, String correlationId) {
		TenantPaymentLocationEntity e = requireLocation(tenantId, id);
		if (e.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "PAYMENT_LOCATION_ALREADY_ACTIVE");
		}
		e.setActive(true);
		e.setUpdatedAt(Instant.now());
		repository.save(e);
		auditService.append(tenantId, actorId, AuditActionCodes.PAYMENT_LOCATION_ACTIVATED,
				AuditResourceTypes.TENANT_PAYMENT_LOCATION, id.toString(), correlationId,
				Map.of("id", id.toString(), "companyId", e.getCompanyId().toString(), "name", e.getName()));
		return toRow(e, true);
	}

	// ─── Deactivate ───────────────────────────────────────────────────────────

	@Transactional
	public TenantPaymentLocationRowDto deactivate(UUID tenantId, UUID id, UUID actorId, String correlationId) {
		TenantPaymentLocationEntity e = requireLocation(tenantId, id);
		if (!e.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "PAYMENT_LOCATION_ALREADY_INACTIVE");
		}
		e.setActive(false);
		e.setUpdatedAt(Instant.now());
		repository.save(e);
		auditService.append(tenantId, actorId, AuditActionCodes.PAYMENT_LOCATION_DEACTIVATED,
				AuditResourceTypes.TENANT_PAYMENT_LOCATION, id.toString(), correlationId,
				Map.of("id", id.toString(), "companyId", e.getCompanyId().toString(), "name", e.getName()));
		return toRow(e, true);
	}

	// ─── Helpers ──────────────────────────────────────────────────────────────

	private TenantPaymentLocationEntity requireLocation(UUID tenantId, UUID id) {
		return repository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private void requireCompany(UUID tenantId, UUID companyId) {
		companyRepository.findByIdAndTenantId(companyId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found in tenant"));
	}

	private TenantBankTemplateEntity requireActiveBankTemplate(UUID tenantId, UUID companyId, UUID bankTemplateId) {
		// BR-6: must belong to same tenant AND company
		TenantBankTemplateEntity bt = bankTemplateRepository.findByIdAndTenantId(bankTemplateId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"bankTemplateId not found in tenant"));
		if (!companyId.equals(bt.getCompanyId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"bankTemplateId does not belong to the specified company");
		}
		// BR-8: must be active
		if (!bt.isActive()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BANK_TEMPLATE_INACTIVE");
		}
		return bt;
	}

	private String requirePaymentType(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentType is required");
		}
		String t = raw.trim().toUpperCase(Locale.ROOT);
		if (!TYPE_CASH.equals(t) && !TYPE_BANK_ACCOUNT.equals(t)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentType must be CASH or BANK_ACCOUNT");
		}
		return t;
	}

	private String requireName(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
		}
		String t = raw.trim();
		if (t.length() > 120) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name exceeds 120 characters");
		}
		return t;
	}

	private String requireCurrency(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency is required");
		}
		String t = raw.trim().toUpperCase(Locale.ROOT);
		if (!ISO_CURRENCY.matcher(t).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY_CODE");
		}
		return t;
	}

	private String normalizeAccountNumber(String raw) {
		String t = raw.trim();
		if (t.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountNumber must not be blank");
		}
		if (t.length() > 60) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountNumber exceeds 60 characters");
		}
		return t;
	}

	/**
	 * BR-7: Validates account number against bank template's account_number_format regex.
	 * If format is null/blank, validation is skipped. If format cannot compile, treated as informational.
	 */
	private void validateAccountNumber(String accountNumber, String format) {
		if (format == null || format.isBlank()) {
			return;
		}
		try {
			Pattern p = Pattern.compile(format);
			if (!p.matcher(accountNumber).matches()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"ACCOUNT_NUMBER_FORMAT_MISMATCH: expected format: " + format);
			}
		} catch (PatternSyntaxException ignored) {
			// BR-7: if format cannot compile, treat as informational and skip
		}
	}

	private String maskAccountNumber(String accountNumber) {
		if (accountNumber == null || accountNumber.length() <= 4) {
			return accountNumber;
		}
		String tail = accountNumber.substring(accountNumber.length() - 4);
		return "\u2022\u2022\u2022\u2022\u2022\u2022" + tail;
	}

	private TenantPaymentLocationRowDto toRow(TenantPaymentLocationEntity e, boolean includeFull) {
		String resolvedBankTemplateName = null;
		String resolvedBankName = null;
		String resolvedSwiftBic = null;
		String resolvedAccountNumberFormat = null;
		if (e.getBankTemplateId() != null) {
			var btOpt = bankTemplateRepository.findById(e.getBankTemplateId());
			if (btOpt.isPresent()) {
				var bt = btOpt.get();
				resolvedBankTemplateName = bt.getName();
				resolvedBankName = bt.getBankName();
				resolvedSwiftBic = bt.getSwiftBic();
				resolvedAccountNumberFormat = bt.getAccountNumberFormat();
			}
		}
		String masked = maskAccountNumber(e.getAccountNumber());
		String full = includeFull ? e.getAccountNumber() : null;
		return new TenantPaymentLocationRowDto(
				e.getId(),
				e.getCompanyId(),
				e.getName(),
				e.getPaymentType(),
				e.getCurrency(),
				e.getBankTemplateId(),
				resolvedBankTemplateName,
				resolvedBankName,
				resolvedSwiftBic,
				resolvedAccountNumberFormat,
				masked,
				full,
				e.isActive(),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	private Map<String, Object> snapshot(TenantPaymentLocationEntity e) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", e.getName());
		m.put("currency", e.getCurrency());
		m.put("bankTemplateId", e.getBankTemplateId() != null ? e.getBankTemplateId().toString() : null);
		m.put("accountNumber", e.getAccountNumber() != null ? "***" : null);
		m.put("active", e.isActive());
		return m;
	}

	private Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
		Map<String, Object> diff = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : after.entrySet()) {
			Object bv = before.get(entry.getKey());
			Object av = entry.getValue();
			boolean changed = (bv == null && av != null) || (bv != null && !bv.equals(av));
			if (changed) {
				diff.put(entry.getKey(), Map.of("from", bv == null ? "" : bv, "to", av == null ? "" : av));
			}
		}
		return diff;
	}
}
