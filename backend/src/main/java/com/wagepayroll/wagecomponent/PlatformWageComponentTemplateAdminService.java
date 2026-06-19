package com.wagepayroll.wagecomponent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.api.dto.PlatformWageComponentTemplateBaseEffectRowDto;
import com.wagepayroll.api.dto.PlatformWageComponentTemplateDependencyRowDto;
import com.wagepayroll.api.dto.PlatformWageComponentTemplateCreateRequest;
import com.wagepayroll.api.dto.PlatformWageComponentTemplatePutLedgerRequest;
import com.wagepayroll.api.dto.PlatformWageComponentTemplatePutRequest;
import com.wagepayroll.api.dto.PlatformWageComponentTemplateRowDto;
import com.wagepayroll.payrollbase.PlatformWageComponentTemplateBaseEffectService;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.domain.country.PlatformCountryRepository;
import com.wagepayroll.domain.ledger.PlatformLedgerTemplateEntity;
import com.wagepayroll.domain.ledger.PlatformLedgerTemplateRepository;
import com.wagepayroll.domain.wagecomponent.PlatformCountryTaxRuleEntity;
import com.wagepayroll.domain.wagecomponent.PlatformCountryTaxRuleRepository;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.formula.FormulaDefinitionConfig;
import com.wagepayroll.payroll.formula.FormulaDefinitionSupport;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.WageComponentSortOrder;
import com.wagepayroll.payroll.model.ComponentType;
import com.wagepayroll.payroll.model.NetEffect;
import com.wagepayroll.payroll.model.PayrollPhase;
import com.wagepayroll.payroll.model.RoundingStrategy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformWageComponentTemplateAdminService {

	private static final int MAX_PAGE_SIZE = 100;

	private final PlatformWageComponentTemplateRepository templateRepository;
	private final PlatformLedgerTemplateRepository ledgerTemplateRepository;
	private final PlatformCountryRepository platformCountryRepository;
	private final PlatformCountryTaxRuleRepository platformCountryTaxRuleRepository;
	private final TenantWageComponentRepository tenantWageComponentRepository;
	private final ObjectMapper objectMapper;
	private final FormulaDefinitionSupport formulaDefinitionSupport;
	private final PlatformWageComponentTemplateBaseEffectService templateBaseEffectService;
	private final PlatformWageComponentTemplateDependencyService templateDependencyService;
	private final AuditService auditService;
	private final WageComponentProcessingOrderService processingOrderService;

	public PlatformWageComponentTemplateAdminService(PlatformWageComponentTemplateRepository templateRepository,
			PlatformLedgerTemplateRepository ledgerTemplateRepository, PlatformCountryRepository platformCountryRepository,
			PlatformCountryTaxRuleRepository platformCountryTaxRuleRepository,
			TenantWageComponentRepository tenantWageComponentRepository, ObjectMapper objectMapper,
			FormulaDefinitionSupport formulaDefinitionSupport,
			PlatformWageComponentTemplateBaseEffectService templateBaseEffectService,
			PlatformWageComponentTemplateDependencyService templateDependencyService, AuditService auditService,
			WageComponentProcessingOrderService processingOrderService) {
		this.templateRepository = templateRepository;
		this.ledgerTemplateRepository = ledgerTemplateRepository;
		this.platformCountryRepository = platformCountryRepository;
		this.platformCountryTaxRuleRepository = platformCountryTaxRuleRepository;
		this.tenantWageComponentRepository = tenantWageComponentRepository;
		this.objectMapper = objectMapper;
		this.formulaDefinitionSupport = formulaDefinitionSupport;
		this.templateBaseEffectService = templateBaseEffectService;
		this.templateDependencyService = templateDependencyService;
		this.auditService = auditService;
		this.processingOrderService = processingOrderService;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(int page, int size, String countryFilter, Boolean activeFilter) {
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Specification<PlatformWageComponentTemplateEntity> spec = (root, q, cb) -> cb.conjunction();
		if (countryFilter != null && !countryFilter.isBlank()) {
			String cc = countryFilter.trim().toUpperCase(Locale.ROOT);
			spec = spec.and((root, q, cb) -> cb.equal(root.get("countryCode"), cc));
		}
		if (activeFilter != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("active"), activeFilter.booleanValue()));
		}
		Page<PlatformWageComponentTemplateEntity> p = templateRepository.findAll(spec, PageRequest.of(safePage, safeSize,
				Sort.by(Sort.Order.asc("countryCode"), Sort.Order.asc("templateCode"))));
		List<UUID> templateIds = p.getContent().stream().map(PlatformWageComponentTemplateEntity::getId).toList();
		Map<UUID, List<PlatformWageComponentTemplateBaseEffectRowDto>> effectsByTemplate = templateBaseEffectService
				.mapByTemplateIds(templateIds);
		Map<UUID, List<PlatformWageComponentTemplateDependencyRowDto>> depsByTemplate = templateDependencyService
				.mapByTemplateIds(templateIds);
		List<PlatformWageComponentTemplateRowDto> items = p.getContent().stream()
				.map(t -> toRow(t, effectsByTemplate.getOrDefault(t.getId(), List.of()),
						depsByTemplate.getOrDefault(t.getId(), List.of())))
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
	public PlatformWageComponentTemplateRowDto get(UUID id) {
		return toRow(templateRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
	}

	@Transactional
	public PlatformWageComponentTemplateRowDto create(PlatformWageComponentTemplateCreateRequest body, UUID actorId,
			String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		String country = normalizeCountry(body.countryCode());
		requirePayrollCountry(country);
		String templateCode = normalizeTemplateCode(body.templateCode());
		if (templateRepository.existsByCountryCodeIgnoreCaseAndTemplateCodeIgnoreCase(country, templateCode)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_TEMPLATE_CODE");
		}
		String json = validateDefinitionJson(body.definitionDefaultsJson(), country);
		Instant now = Instant.now();
		PlatformWageComponentTemplateEntity t = new PlatformWageComponentTemplateEntity();
		t.setId(UUID.randomUUID());
		t.setCountryCode(country);
		t.setTemplateCode(templateCode);
		t.setCreatedAt(now);
		t.setUpdatedAt(now);
		applyWritableFields(t, body.name(), body.description(), json, body.processingOrderHint(), body.phaseHint(),
				body.debitPlatformLedgerTemplateId(), body.creditPlatformLedgerTemplateId(), country, body.duplicable(),
				body.printOnPayslip(), body.auxiliary(), body.applyInPayroll(), body.recurrence(), body.countryRuleKey(),
				body.platformCountryTaxRuleId(), body.active());
		templateRepository.save(t);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_WAGE_COMPONENT_TEMPLATE_CREATED,
				AuditResourceTypes.PLATFORM_WAGE_COMPONENT_TEMPLATE, t.getId().toString(), correlationId, Map.of("id", t.getId().toString()));
		return toRow(t);
	}

	@Transactional
	public PlatformWageComponentTemplateRowDto update(UUID id, PlatformWageComponentTemplatePutRequest body, UUID actorId,
			String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		PlatformWageComponentTemplateEntity t = templateRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		String country = normalizeCountry(t.getCountryCode());
		String json = validateDefinitionJson(body.definitionDefaultsJson(), country);
		applyWritableFields(t, body.name(), body.description(), json, body.processingOrderHint(), body.phaseHint(),
				body.debitPlatformLedgerTemplateId(), body.creditPlatformLedgerTemplateId(), country, body.duplicable(),
				body.printOnPayslip(), body.auxiliary(), body.applyInPayroll(), body.recurrence(), body.countryRuleKey(),
				body.platformCountryTaxRuleId(), body.active());
		t.setUpdatedAt(Instant.now());
		templateRepository.save(t);
		if (body.baseEffects() != null) {
			templateBaseEffectService.replaceForTemplate(id, body.baseEffects());
		}
		if (body.dependencies() != null) {
			templateDependencyService.replaceForTemplate(id, body.dependencies());
			auditService.append(null, actorId, AuditActionCodes.PLATFORM_WAGE_COMPONENT_TEMPLATE_DEPENDENCIES_UPDATED,
					AuditResourceTypes.PLATFORM_WAGE_COMPONENT_TEMPLATE, id.toString(), correlationId,
					Map.of("id", id.toString()));
		}
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_WAGE_COMPONENT_TEMPLATE_UPDATED,
				AuditResourceTypes.PLATFORM_WAGE_COMPONENT_TEMPLATE, id.toString(), correlationId, Map.of("id", id.toString()));
		return toRow(t);
	}

	@Transactional
	public void delete(UUID id, UUID actorId, String correlationId) {
		PlatformWageComponentTemplateEntity t = templateRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (tenantWageComponentRepository.countByPlatformTemplateId(id) > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "TEMPLATE_IN_USE");
		}
		templateRepository.delete(t);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_WAGE_COMPONENT_TEMPLATE_DELETED,
				AuditResourceTypes.PLATFORM_WAGE_COMPONENT_TEMPLATE, id.toString(), correlationId, Map.of("id", id.toString()));
	}

	@Transactional
	public PlatformWageComponentTemplateRowDto putLedgerLinks(UUID id, PlatformWageComponentTemplatePutLedgerRequest body,
			UUID actorId, String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		PlatformWageComponentTemplateEntity t = templateRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		String country = normalizeCountry(t.getCountryCode());
		Map<String, Object> before = ledgerSnapshot(t);
		validateLedgerRef(country, body.debitPlatformLedgerTemplateId());
		validateLedgerRef(country, body.creditPlatformLedgerTemplateId());
		t.setDebitPlatformLedgerTemplateId(body.debitPlatformLedgerTemplateId());
		t.setCreditPlatformLedgerTemplateId(body.creditPlatformLedgerTemplateId());
		t.setUpdatedAt(Instant.now());
		templateRepository.save(t);
		Map<String, Object> after = ledgerSnapshot(t);
		Map<String, Object> changes = diff(before, after);
		if (!changes.isEmpty()) {
			Map<String, Object> meta = new LinkedHashMap<>();
			meta.put("id", id.toString());
			meta.put("changes", changes);
			auditService.append(null, actorId, AuditActionCodes.PLATFORM_WAGE_COMPONENT_TEMPLATE_LEDGER_LINKS_UPDATED,
					AuditResourceTypes.PLATFORM_WAGE_COMPONENT_TEMPLATE, id.toString(), correlationId, meta);
		}
		return toRow(t);
	}

	private void applyWritableFields(PlatformWageComponentTemplateEntity t, String name, String description, String defJson,
			Integer processingOrderHint, String phaseHint, UUID debitPlatformLedgerTemplateId, UUID creditPlatformLedgerTemplateId,
			String country, boolean duplicable, boolean printOnPayslip, boolean auxiliary, boolean applyInPayroll,
			String recurrence, String countryRuleKey, UUID platformCountryTaxRuleId, boolean active) {
		t.setName(name.trim());
		t.setDescription(trimToNull(description));
		int canonicalOrder = WageComponentSortOrder.forTemplateCode(t.getTemplateCode());
		t.setDefinitionDefaultsJson(processingOrderService.patchJsonProcessingOrder(defJson, canonicalOrder));
		t.setProcessingOrderHint(canonicalOrder);
		t.setPhaseHint(trimToNull(phaseHint));
		validateLedgerRef(country, debitPlatformLedgerTemplateId);
		validateLedgerRef(country, creditPlatformLedgerTemplateId);
		t.setDebitPlatformLedgerTemplateId(debitPlatformLedgerTemplateId);
		t.setCreditPlatformLedgerTemplateId(creditPlatformLedgerTemplateId);
		t.setDuplicable(duplicable);
		t.setPrintOnPayslip(printOnPayslip);
		t.setAuxiliary(auxiliary);
		t.setApplyInPayroll(applyInPayroll);
		t.setRecurrence(trimToNull(recurrence));
		t.setCountryRuleKey(trimToNull(countryRuleKey));
		validatePlatformTaxRuleForCountry(country, platformCountryTaxRuleId);
		t.setPlatformCountryTaxRuleId(platformCountryTaxRuleId);
		t.setActive(active);
	}

	private String validateDefinitionJson(String raw, String payrollCountry) {
		if (!StringUtils.hasText(raw)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_DEFINITION_DEFAULTS_JSON");
		}
		String trimmed = raw.trim();
		DefinitionDefaults def;
		try {
			def = objectMapper.readValue(trimmed, DefinitionDefaults.class);
		}
		catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_DEFINITION_DEFAULTS_JSON");
		}
		CalculationMethod method = def.calculationMethod != null ? def.calculationMethod : CalculationMethod.FIXED_AMOUNT;
		FormulaDefinitionConfig formulaConfig = formulaDefinitionSupport.configFrom(def.formulaMode, def.formulaRules,
				def.defaultFormulaExpression, def.formulaExpression);
		formulaDefinitionSupport.validate(method, trimToNull(def.percentageBase), formulaConfig, Set.of());
		return trimmed;
	}

	private void requirePayrollCountry(String country) {
		if (!platformCountryRepository.existsActivePayrollEnabledByIsoAlpha2(country)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_OR_INACTIVE_COUNTRY");
		}
	}

	private void validatePlatformTaxRuleForCountry(String payrollCountry, UUID ruleId) {
		if (ruleId == null) {
			return;
		}
		PlatformCountryTaxRuleEntity rule = platformCountryTaxRuleRepository.findById(ruleId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_PLATFORM_TAX_RULE"));
		if (!payrollCountry.equals(normalizeCountry(rule.getCountryCode()))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLATFORM_TAX_RULE_COUNTRY_MISMATCH");
		}
	}

	private void validateLedgerRef(String country, UUID ledgerTemplateId) {
		if (ledgerTemplateId == null) {
			return;
		}
		PlatformLedgerTemplateEntity lt = ledgerTemplateRepository.findById(ledgerTemplateId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_LEDGER_TEMPLATE"));
		if (!country.equals(normalizeCountry(lt.getCountryCode()))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LEDGER_TEMPLATE_COUNTRY_MISMATCH");
		}
		if (!lt.isActive()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LEDGER_TEMPLATE_INACTIVE");
		}
	}

	private static String normalizeCountry(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		return raw.trim().toUpperCase(Locale.ROOT);
	}

	private static String normalizeTemplateCode(String raw) {
		if (!StringUtils.hasText(raw)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE_CODE");
		}
		String upper = raw.trim().toUpperCase(Locale.ROOT);
		for (int i = 0; i < upper.length(); i++) {
			char c = upper.charAt(i);
			if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
				continue;
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE_CODE");
		}
		if (upper.length() > 64) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEMPLATE_CODE_TOO_LONG");
		}
		return upper;
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private static Map<String, Object> ledgerSnapshot(PlatformWageComponentTemplateEntity t) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("debitPlatformLedgerTemplateId", t.getDebitPlatformLedgerTemplateId());
		m.put("creditPlatformLedgerTemplateId", t.getCreditPlatformLedgerTemplateId());
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

	private PlatformWageComponentTemplateRowDto toRow(PlatformWageComponentTemplateEntity t) {
		return toRow(t, templateBaseEffectService.listForTemplate(t.getId()),
				templateDependencyService.listForTemplate(t.getId()));
	}

	private static PlatformWageComponentTemplateRowDto toRow(PlatformWageComponentTemplateEntity t,
			List<PlatformWageComponentTemplateBaseEffectRowDto> baseEffects,
			List<PlatformWageComponentTemplateDependencyRowDto> dependencies) {
		return new PlatformWageComponentTemplateRowDto(t.getId(), t.getCountryCode(), t.getTemplateCode(), t.getName(),
				t.getDescription(), t.getDefinitionDefaultsJson(), t.getProcessingOrderHint(), t.getPhaseHint(),
				t.getDebitPlatformLedgerTemplateId(), t.getCreditPlatformLedgerTemplateId(), t.isDuplicable(),
				t.isPrintOnPayslip(), t.isAuxiliary(), t.isApplyInPayroll(), t.getRecurrence(), t.getCountryRuleKey(),
				t.getPlatformCountryTaxRuleId(), t.isActive(), baseEffects, dependencies, t.getCreatedAt(), t.getUpdatedAt());
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class DefinitionDefaults {
		public ComponentType componentType;
		public String category;
		public NetEffect netEffect;
		public CalculationMethod calculationMethod;
		public PayrollPhase phase;
		public Integer processingOrder;
		public Boolean taxableWageTax;
		public Boolean taxableSocialSecurity;
		public Boolean taxablePension;
		public Boolean taxableVacationReserve;
		public String percentageBase;
		public String formulaMode;
		public List<FormulaDefinitionSupport.FormulaRuleJson> formulaRules;
		public String defaultFormulaExpression;
		public String formulaExpression;
		public BigDecimal defaultAmount;
		public RoundingStrategy roundingStrategy;
		public Boolean maintainsBalance;
		public String balanceType;
		public String balanceDirection;
		public UUID counterComponentId;
		public String postingStrategy;
	}
}
