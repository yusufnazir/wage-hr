package com.wagepayroll.wagecomponent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.api.dto.PlatformStatutoryWageComponentRowDto;
import com.wagepayroll.api.dto.TenantWageComponentCreateRequest;
import com.wagepayroll.api.dto.TenantWageComponentPutRequest;
import com.wagepayroll.api.dto.TenantWageComponentRowDto;
import com.wagepayroll.api.dto.TenantWageComponentTemplateCatalogRowDto;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.wagecomponent.PlatformCountryTaxRuleEntity;
import com.wagepayroll.domain.wagecomponent.PlatformCountryTaxRuleRepository;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentRepository;
import com.wagepayroll.domain.ledger.TenantLedgerEntity;
import com.wagepayroll.domain.ledger.TenantLedgerRepository;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.formula.FormulaDefinitionConfig;
import com.wagepayroll.payroll.formula.FormulaDefinitionSupport;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.WageComponentSortOrder;
import com.wagepayroll.payroll.model.ComponentType;
import com.wagepayroll.payroll.base.ComponentDependencyCopyService;
import com.wagepayroll.payroll.base.WageComponentBaseEffectCopyService;
import com.wagepayroll.wagecomponent.PlatformWageComponentTemplateDependencyService;
import com.wagepayroll.payroll.model.NetEffect;
import com.wagepayroll.payroll.model.PayrollImpactSide;
import com.wagepayroll.payroll.model.PayrollPhase;
import com.wagepayroll.payroll.model.RoundingStrategy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantWageComponentService {

	private static final int MAX_PAGE_SIZE = 100;

	private final TenantWageComponentRepository tenantWageComponentRepository;
	private final TenantCompanyRepository companyRepository;
	private final PlatformWageComponentTemplateRepository templateRepository;
	private final PlatformWageComponentRepository platformWageComponentRepository;
	private final PlatformCountryTaxRuleRepository platformCountryTaxRuleRepository;
	private final TenantLedgerRepository tenantLedgerRepository;
	private final ObjectMapper objectMapper;
	private final FormulaDefinitionSupport formulaDefinitionSupport;
	private final WageComponentBaseEffectCopyService baseEffectCopyService;
	private final ComponentDependencyCopyService componentDependencyCopyService;
	private final PlatformWageComponentTemplateDependencyService templateDependencyService;

	public TenantWageComponentService(TenantWageComponentRepository tenantWageComponentRepository,
			TenantCompanyRepository companyRepository, PlatformWageComponentTemplateRepository templateRepository,
			PlatformWageComponentRepository platformWageComponentRepository,
			PlatformCountryTaxRuleRepository platformCountryTaxRuleRepository, 			TenantLedgerRepository tenantLedgerRepository,
			ObjectMapper objectMapper, FormulaDefinitionSupport formulaDefinitionSupport,
			WageComponentBaseEffectCopyService baseEffectCopyService,
			ComponentDependencyCopyService componentDependencyCopyService,
			PlatformWageComponentTemplateDependencyService templateDependencyService) {
		this.tenantWageComponentRepository = tenantWageComponentRepository;
		this.companyRepository = companyRepository;
		this.templateRepository = templateRepository;
		this.platformWageComponentRepository = platformWageComponentRepository;
		this.platformCountryTaxRuleRepository = platformCountryTaxRuleRepository;
		this.tenantLedgerRepository = tenantLedgerRepository;
		this.objectMapper = objectMapper;
		this.formulaDefinitionSupport = formulaDefinitionSupport;
		this.baseEffectCopyService = baseEffectCopyService;
		this.componentDependencyCopyService = componentDependencyCopyService;
		this.templateDependencyService = templateDependencyService;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(UUID tenantId, UUID companyId, int page, int size, Boolean activeFilter) {
		if (companyId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyId is required");
		}
		requireCompanyEntity(tenantId, companyId);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		// List sort: processingOrder is display order (from template), not payroll engine sequence.
		Page<TenantWageComponentEntity> p = activeFilter == null
				? tenantWageComponentRepository.findByTenantIdAndCompanyId(tenantId, companyId,
						PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("processingOrder"), Sort.Order.asc("code"))))
				: tenantWageComponentRepository.findByTenantIdAndCompanyIdAndActive(tenantId, companyId,
						activeFilter.booleanValue(),
						PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("processingOrder"), Sort.Order.asc("code"))));
		Map<UUID, String> tplCodes = loadTemplateCodesById(p.getContent());
		List<TenantWageComponentRowDto> items = p.getContent().stream()
				.map(e -> toRow(e, e.getPlatformTemplateId() != null ? tplCodes.get(e.getPlatformTemplateId()) : null))
				.toList();
		Map<String, Object> out = new HashMap<>();
		out.put("data", items);
		out.put("page", Map.of("number", p.getNumber(), "size", p.getSize(), "totalElements", p.getTotalElements(), "totalPages",
				p.getTotalPages()));
		return out;
	}

	@Transactional(readOnly = true)
	public TenantWageComponentRowDto get(UUID tenantId, UUID id) {
		TenantWageComponentEntity e = tenantWageComponentRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		String templateCode = null;
		if (e.getPlatformTemplateId() != null) {
			templateCode = templateRepository.findById(e.getPlatformTemplateId())
					.map(PlatformWageComponentTemplateEntity::getTemplateCode)
					.orElse(null);
		}
		return toRow(e, templateCode);
	}

	@Transactional(readOnly = true)
	public List<TenantWageComponentTemplateCatalogRowDto> listTemplatesForCompany(UUID tenantId, UUID companyId) {
		TenantCompanyEntity company = requireCompanyEntity(tenantId, companyId);
		String country = normalizeCountry(company.getPayrollCountry());
		return templateRepository.findByCountryCodeAndActiveIsTrueOrderByTemplateCodeAsc(country).stream()
				.map(this::toTemplateCatalogRow)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<PlatformStatutoryWageComponentRowDto> listStatutoryForCompany(UUID tenantId, UUID companyId) {
		TenantCompanyEntity company = requireCompanyEntity(tenantId, companyId);
		String country = normalizeCountry(company.getPayrollCountry());
		return platformWageComponentRepository.findByCountryCodeAndActiveIsTrueOrderByProcessingOrderAsc(country).stream()
				.filter(PlatformWageComponentEntity::isStatutory)
				.map(this::toStatutoryRow)
				.toList();
	}

	/**
	 * Creates a tenant wage component from a platform template when missing (company bootstrap).
	 * Skips the {@code duplicable} guard used by the public create API.
	 */
	@Transactional
	public UUID provisionFromPlatformTemplateIfAbsent(UUID tenantId, UUID companyId, UUID platformTemplateId) {
		return tenantWageComponentRepository
				.findByTenantIdAndCompanyIdAndPlatformTemplateId(tenantId, companyId, platformTemplateId)
				.map(existing -> {
					applyTemplateMirrorsToTenantRow(existing, platformTemplateId);
					return existing.getId();
				})
				.orElseGet(() -> createTenantRowFromPlatformTemplate(tenantId, companyId, platformTemplateId).getId());
	}

	/**
	 * Keeps tenant rows aligned with their platform template (phase, sort order, country algorithms).
	 * Used when catalog templates evolve; fresh databases get the same result on first provision.
	 */
	private void applyTemplateMirrorsToTenantRow(TenantWageComponentEntity tenantRow, UUID platformTemplateId) {
		PlatformWageComponentTemplateEntity template = templateRepository.findById(platformTemplateId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_TEMPLATE"));
		DefinitionDefaults def = parseDefaults(template.getDefinitionDefaultsJson());
		tenantRow.setComponentType(def.componentType != null ? def.componentType : tenantRow.getComponentType());
		tenantRow.setCategory(def.category != null && !def.category.isBlank() ? def.category.trim() : tenantRow.getCategory());
		tenantRow.setNetEffect(def.netEffect != null ? def.netEffect : tenantRow.getNetEffect());
		if (def.taxableWageTax != null) {
			tenantRow.setTaxableWageTax(def.taxableWageTax);
		}
		if (def.taxableSocialSecurity != null) {
			tenantRow.setTaxableSocialSecurity(def.taxableSocialSecurity);
		}
		if (def.taxablePension != null) {
			tenantRow.setTaxablePension(def.taxablePension);
		}
		if (def.taxableVacationReserve != null) {
			tenantRow.setTaxableVacationReserve(def.taxableVacationReserve);
		}
		if (def.calculationMethod != null) {
			tenantRow.setCalculationMethod(def.calculationMethod);
		}
		tenantRow.setPhase(def.phase != null ? def.phase : tenantRow.getPhase());
		tenantRow.setProcessingOrder(WageComponentSortOrder.resolve(tenantRow.getComponentType(), tenantRow.getPhase(),
				tenantRow.getCategory(), tenantRow.isTaxableWageTax(), template.getTemplateCode(), def.processingOrder));
		tenantRow.setCountryRuleKey(trimToNull(template.getCountryRuleKey()));
		tenantRow.setPlatformCountryTaxRuleId(template.getPlatformCountryTaxRuleId());
		tenantRow.setUpdatedAt(Instant.now());
		tenantWageComponentRepository.save(tenantRow);
	}

	@Transactional
	public TenantWageComponentRowDto createFromTemplate(UUID tenantId, TenantWageComponentCreateRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		TenantCompanyEntity company = requireCompanyEntity(tenantId, body.companyId());
		String country = normalizeCountry(company.getPayrollCountry());
		PlatformWageComponentTemplateEntity template = templateRepository.findById(body.platformTemplateId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_TEMPLATE"));
		if (!template.isActive()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEMPLATE_INACTIVE");
		}
		if (!country.equals(normalizeCountry(template.getCountryCode()))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEMPLATE_COUNTRY_MISMATCH");
		}
		if (!template.isDuplicable()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEMPLATE_NOT_DUPLICABLE");
		}
		String templateCode = template.getTemplateCode();
		String normalizedSuffix = normalizeCodeSuffix(body.codeSuffix());
		String code = buildTenantComponentCode(templateCode, normalizedSuffix);
		validateTenantCode(tenantId, body.companyId(), country, code, null);
		String name = StringUtils.hasText(body.name()) ? body.name().trim() : template.getName();
		if (!StringUtils.hasText(name)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
		}
		TenantWageComponentEntity e = createTenantRowFromPlatformTemplate(tenantId, body.companyId(), template.getId(), code, name);
		return toRow(e, templateCode);
	}

	private TenantWageComponentEntity createTenantRowFromPlatformTemplate(UUID tenantId, UUID companyId, UUID platformTemplateId) {
		PlatformWageComponentTemplateEntity template = templateRepository.findById(platformTemplateId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_TEMPLATE"));
		TenantCompanyEntity company = requireCompanyEntity(tenantId, companyId);
		String country = normalizeCountry(company.getPayrollCountry());
		if (!template.isActive()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEMPLATE_INACTIVE");
		}
		if (!country.equals(normalizeCountry(template.getCountryCode()))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEMPLATE_COUNTRY_MISMATCH");
		}
		String templateCode = template.getTemplateCode();
		if (!StringUtils.hasText(templateCode)) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "MISSING_TEMPLATE_CODE");
		}
		String code = buildTenantComponentCode(templateCode, "");
		validateTenantCode(tenantId, companyId, country, code, null);
		return createTenantRowFromPlatformTemplate(tenantId, companyId, platformTemplateId, code, template.getName());
	}

	private TenantWageComponentEntity createTenantRowFromPlatformTemplate(UUID tenantId, UUID companyId, UUID platformTemplateId,
			String code, String name) {
		PlatformWageComponentTemplateEntity template = templateRepository.findById(platformTemplateId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_TEMPLATE"));
		TenantCompanyEntity company = requireCompanyEntity(tenantId, companyId);
		String country = normalizeCountry(company.getPayrollCountry());
		validatePlatformTaxRuleForPayrollCountry(country, template.getPlatformCountryTaxRuleId());
		DefinitionDefaults def = parseDefaults(template.getDefinitionDefaultsJson());
		Instant now = Instant.now();
		TenantWageComponentEntity e = new TenantWageComponentEntity();
		e.setId(UUID.randomUUID());
		e.setTenantId(tenantId);
		e.setCompanyId(companyId);
		e.setPlatformTemplateId(template.getId());
		e.setCode(code);
		e.setName(name);
		e.setDescription(template.getDescription());
		e.setComponentType(def.componentType != null ? def.componentType : ComponentType.EARNING);
		e.setCategory(def.category != null && !def.category.isBlank() ? def.category.trim() : "GENERAL");
		e.setNetEffect(def.netEffect != null ? def.netEffect : NetEffect.ADD_TO_NET);
		e.setTaxableWageTax(def.taxableWageTax != null && def.taxableWageTax);
		e.setTaxableSocialSecurity(def.taxableSocialSecurity != null && def.taxableSocialSecurity);
		e.setTaxablePension(def.taxablePension != null && def.taxablePension);
		e.setTaxableVacationReserve(def.taxableVacationReserve != null && def.taxableVacationReserve);
		e.setCalculationMethod(def.calculationMethod != null ? def.calculationMethod : CalculationMethod.FIXED_AMOUNT);
		e.setPercentageBase(trimToNull(def.percentageBase));
		FormulaDefinitionConfig formulaConfig = formulaDefinitionSupport.configFrom(def.formulaMode, def.formulaRules,
				def.defaultFormulaExpression, def.formulaExpression);
		e.setFormulaExpression(trimToNull(formulaDefinitionSupport.toStoredExpression(formulaConfig)));
		e.setDefaultAmount(def.defaultAmount);
		e.setRoundingStrategy(def.roundingStrategy != null ? def.roundingStrategy : RoundingStrategy.HALF_UP);
		e.setProcessingOrder(WageComponentSortOrder.resolve(e.getComponentType(), e.getPhase(), e.getCategory(),
				e.isTaxableWageTax(), template.getTemplateCode(), def.processingOrder));
		e.setPhase(def.phase != null ? def.phase : PayrollPhase.GROSS);
		e.setMaintainsBalance(def.maintainsBalance != null && def.maintainsBalance);
		e.setBalanceType(trimToNull(def.balanceType));
		e.setBalanceDirection(trimToNull(def.balanceDirection));
		e.setCounterComponentId(def.counterComponentId);
		e.setPostingStrategy(trimToNull(def.postingStrategy));
		Map<UUID, UUID> platformLedgerToTenant = tenantLedgerRepository
				.findByTenantIdAndCompanyIdOrderByCodeAsc(tenantId, companyId).stream()
				.collect(Collectors.toMap(TenantLedgerEntity::getPlatformLedgerTemplateId, TenantLedgerEntity::getId, (a, b) -> a));
		if (template.getDebitPlatformLedgerTemplateId() != null) {
			e.setDebitTenantLedgerId(platformLedgerToTenant.get(template.getDebitPlatformLedgerTemplateId()));
		}
		if (template.getCreditPlatformLedgerTemplateId() != null) {
			e.setCreditTenantLedgerId(platformLedgerToTenant.get(template.getCreditPlatformLedgerTemplateId()));
		}
		e.setPrintOnPayslip(template.isPrintOnPayslip());
		e.setAuxiliary(template.isAuxiliary());
		e.setApplyInPayroll(template.isApplyInPayroll());
		e.setRecurrence(trimToNull(template.getRecurrence()));
		e.setCountryRuleKey(trimToNull(template.getCountryRuleKey()));
		e.setPlatformCountryTaxRuleId(template.getPlatformCountryTaxRuleId());
		e.setImpactSide(def.impactSide != null ? def.impactSide : PayrollImpactSide.EMPLOYEE);
		e.setActive(true);
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		java.util.Set<String> prerequisiteCodes = templateDependencyService.prerequisiteTemplateCodesFor(template.getId());
		formulaDefinitionSupport.validate(e.getCalculationMethod(), e.getPercentageBase(), formulaConfig, prerequisiteCodes);
		TenantWageComponentEntity saved = tenantWageComponentRepository.save(e);
		baseEffectCopyService.copyTemplateEffectsToTenantComponent(tenantId, template.getId(), saved.getId());
		componentDependencyCopyService.copyTemplateDependenciesToTenantComponent(tenantId, companyId, template.getId(),
				saved.getId());
		return saved;
	}

	@Transactional
	public TenantWageComponentRowDto update(UUID tenantId, UUID id, TenantWageComponentPutRequest body) {
		TenantWageComponentEntity e = tenantWageComponentRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		requireFromTemplate(e);
		if (!e.getCompanyId().equals(body.companyId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPANY_MISMATCH");
		}
		TenantCompanyEntity company = requireCompanyEntity(tenantId, body.companyId());
		String country = normalizeCountry(company.getPayrollCountry());
		PlatformWageComponentTemplateEntity template = templateRepository.findById(e.getPlatformTemplateId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "MISSING_TEMPLATE"));
		String templateCode = template.getTemplateCode();
		if (!StringUtils.hasText(templateCode)) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "MISSING_TEMPLATE_CODE");
		}
		String normalizedSuffix = normalizeCodeSuffix(body.codeSuffix());
		String newCode = buildTenantComponentCode(templateCode, normalizedSuffix);
		if (!newCode.equalsIgnoreCase(e.getCode())) {
			validateTenantCode(tenantId, body.companyId(), country, newCode, e.getId());
		}
		requireLedgerForCompany(tenantId, body.companyId(), body.debitTenantLedgerId());
		requireLedgerForCompany(tenantId, body.companyId(), body.creditTenantLedgerId());
		e.setCode(newCode);
		e.setName(body.name().trim());
		e.setDebitTenantLedgerId(body.debitTenantLedgerId());
		e.setCreditTenantLedgerId(body.creditTenantLedgerId());
		e.setPrintOnPayslip(body.printOnPayslip());
		e.setActive(body.active());
		if (body.formulaExpression() != null) {
			if (e.getCalculationMethod() != CalculationMethod.FORMULA) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_NOT_APPLICABLE");
			}
			FormulaDefinitionConfig config = formulaDefinitionSupport.parseStoredExpression(body.formulaExpression());
			java.util.Set<String> prerequisiteCodes = templateDependencyService.prerequisiteTemplateCodesFor(template.getId());
			formulaDefinitionSupport.validate(e.getCalculationMethod(), e.getPercentageBase(), config, prerequisiteCodes);
			String stored = formulaDefinitionSupport.toStoredExpression(config);
			if (stored != null && stored.length() > 500) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_TOO_LONG");
			}
			e.setFormulaExpression(trimToNull(stored));
		}
		e.setUpdatedAt(Instant.now());
		tenantWageComponentRepository.save(e);
		return toRow(e, templateCode);
	}

	@Transactional
	public TenantWageComponentRowDto patchActive(UUID tenantId, UUID id, boolean active) {
		TenantWageComponentEntity e = tenantWageComponentRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		requireFromTemplate(e);
		e.setActive(active);
		e.setUpdatedAt(Instant.now());
		tenantWageComponentRepository.save(e);
		String templateCode = templateRepository.findById(e.getPlatformTemplateId())
				.map(PlatformWageComponentTemplateEntity::getTemplateCode)
				.orElse(null);
		return toRow(e, templateCode);
	}

	private void requireLedgerForCompany(UUID tenantId, UUID companyId, UUID ledgerId) {
		if (ledgerId == null) {
			return;
		}
		TenantLedgerEntity row = tenantLedgerRepository.findByIdAndTenantId(ledgerId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_TENANT_LEDGER"));
		if (!row.getCompanyId().equals(companyId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LEDGER_COMPANY_MISMATCH");
		}
	}

	private void requireFromTemplate(TenantWageComponentEntity e) {
		if (e.getPlatformTemplateId() == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "TEMPLATE_REQUIRED");
		}
	}

	private Map<UUID, String> loadTemplateCodesById(List<TenantWageComponentEntity> entities) {
		List<UUID> ids = entities.stream().map(TenantWageComponentEntity::getPlatformTemplateId).filter(Objects::nonNull).distinct()
				.toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return templateRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(PlatformWageComponentTemplateEntity::getId, PlatformWageComponentTemplateEntity::getTemplateCode));
	}

	private static String normalizeCodeSuffix(String raw) {
		if (raw == null) {
			return "";
		}
		String t = raw.trim();
		if (t.isEmpty()) {
			return "";
		}
		String upper = t.toUpperCase(Locale.ROOT);
		for (int i = 0; i < upper.length(); i++) {
			char c = upper.charAt(i);
			if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
				continue;
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CODE_SUFFIX");
		}
		return upper;
	}

	private static String buildTenantComponentCode(String templateCode, String normalizedSuffix) {
		if (normalizedSuffix.isEmpty()) {
			if (templateCode.length() > 64) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CODE_TOO_LONG");
			}
			return templateCode;
		}
		String combined = templateCode + "_" + normalizedSuffix;
		if (combined.length() > 64) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CODE_TOO_LONG");
		}
		return combined;
	}

	private void validateTenantCode(UUID tenantId, UUID companyId, String payrollCountry, String code, UUID excludeComponentId) {
		boolean duplicate = excludeComponentId == null
				? tenantWageComponentRepository.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, code)
				: tenantWageComponentRepository.existsByTenantIdAndCompanyIdAndCodeIgnoreCaseAndIdNot(tenantId, companyId, code,
						excludeComponentId);
		if (duplicate) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_CODE");
		}
		if (platformWageComponentRepository.existsByCountryCodeAndCodeIgnoreCaseAndStatutoryIsTrueAndActiveIsTrue(
				payrollCountry, code)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STATUTORY_CODE_RESERVED");
		}
	}

	private DefinitionDefaults parseDefaults(String json) {
		if (!StringUtils.hasText(json)) {
			return new DefinitionDefaults();
		}
		try {
			return objectMapper.readValue(json, DefinitionDefaults.class);
		}
		catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "TEMPLATE_DEFAULTS_INVALID");
		}
	}

	private TenantCompanyEntity requireCompanyEntity(UUID tenantId, UUID companyId) {
		return companyRepository.findByIdAndTenantId(companyId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_COMPANY"));
	}

	private static String normalizeCountry(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		return raw.trim().toUpperCase(Locale.ROOT);
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private TenantWageComponentRowDto toRow(TenantWageComponentEntity e, String templateCode) {
		return new TenantWageComponentRowDto(e.getId(), e.getCompanyId(), e.getPlatformTemplateId(), templateCode, e.getCode(),
				e.getName(), e.getDescription(), e.getComponentType().name(), e.getCategory(), e.getNetEffect().name(),
				e.isTaxableWageTax(), e.isTaxableSocialSecurity(), e.isTaxablePension(), e.isTaxableVacationReserve(),
				e.getCalculationMethod().name(), e.getPercentageBase(), e.getFormulaExpression(), e.getDefaultAmount(),
				e.getRoundingStrategy().name(), e.getProcessingOrder(), e.getPhase().name(), e.isMaintainsBalance(), e.getBalanceType(),
				e.getBalanceDirection(), e.getCounterComponentId(), e.getDebitTenantLedgerId(), e.getCreditTenantLedgerId(),
				e.getPostingStrategy(), e.isPrintOnPayslip(), e.isAuxiliary(), e.isApplyInPayroll(), e.getRecurrence(),
				e.getCountryRuleKey(), e.getPlatformCountryTaxRuleId(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
	}

	private TenantWageComponentTemplateCatalogRowDto toTemplateCatalogRow(PlatformWageComponentTemplateEntity t) {
		return new TenantWageComponentTemplateCatalogRowDto(t.getId(), t.getCountryCode(), t.getTemplateCode(), t.getName(),
				t.getDescription(), t.getProcessingOrderHint(), t.getPhaseHint(), t.getDebitPlatformLedgerTemplateId(),
				t.getCreditPlatformLedgerTemplateId(), t.isDuplicable(), t.isPrintOnPayslip(), t.isAuxiliary(), t.isApplyInPayroll(),
				t.getRecurrence(), t.getCountryRuleKey(), t.getPlatformCountryTaxRuleId());
	}

	private void validatePlatformTaxRuleForPayrollCountry(String payrollCountry, UUID ruleId) {
		if (ruleId == null) {
			return;
		}
		PlatformCountryTaxRuleEntity rule = platformCountryTaxRuleRepository.findById(ruleId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_PLATFORM_TAX_RULE"));
		if (!payrollCountry.equals(normalizeCountry(rule.getCountryCode()))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLATFORM_TAX_RULE_COUNTRY_MISMATCH");
		}
	}

	private PlatformStatutoryWageComponentRowDto toStatutoryRow(PlatformWageComponentEntity p) {
		return new PlatformStatutoryWageComponentRowDto(p.getId(), p.getCountryCode(), p.getCode(), p.getName(), p.getDescription(),
				p.isStatutory(), p.getComponentType().name(), p.getCategory(), p.getNetEffect().name(), p.getCalculationMethod().name(),
				p.getProcessingOrder(), p.getPhase().name(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt());
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
		public PayrollImpactSide impactSide;
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
