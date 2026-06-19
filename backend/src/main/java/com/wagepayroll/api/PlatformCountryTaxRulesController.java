package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PlatformCountryTaxRuleCreateRequest;
import com.wagepayroll.api.dto.PlatformCountryTaxRulePutRequest;
import com.wagepayroll.api.dto.PlatformCountryTaxRuleRowDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.PlatformOperatorService;
import com.wagepayroll.wagecomponent.PlatformCountryTaxRuleAdminService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/country-tax-rules")
public class PlatformCountryTaxRulesController {

	private final PlatformOperatorService platformOperatorService;

	private final PlatformCountryTaxRuleAdminService taxRuleAdminService;

	private final AuditService auditService;

	public PlatformCountryTaxRulesController(PlatformOperatorService platformOperatorService,
			PlatformCountryTaxRuleAdminService taxRuleAdminService, AuditService auditService) {
		this.platformOperatorService = platformOperatorService;
		this.taxRuleAdminService = taxRuleAdminService;
		this.auditService = auditService;
	}

	@GetMapping
	public ApiResponse<Map<String, Object>> list(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "50") int size,
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "active", required = false) Boolean active,
			@RequestParam(name = "search", required = false) String search,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(taxRuleAdminService.list(page, size, country, active, search), rid);
	}

	@GetMapping("/{id}")
	public ApiResponse<Map<String, PlatformCountryTaxRuleRowDto>> get(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		PlatformCountryTaxRuleRowDto row = taxRuleAdminService.get(id);
		return ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, PlatformCountryTaxRuleRowDto>>> create(
			@RequestBody PlatformCountryTaxRuleCreateRequest body, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformCountryTaxRuleRowDto row = taxRuleAdminService.create(body);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_COUNTRY_TAX_RULE_CREATED,
				AuditResourceTypes.PLATFORM_COUNTRY_TAX_RULE, row.id().toString(), rid,
				Map.of("countryCode", row.countryCode(), "ruleCode", row.ruleCode()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("item", row), rid));
	}

	@PutMapping("/{id}")
	public ApiResponse<Map<String, PlatformCountryTaxRuleRowDto>> put(@PathVariable("id") UUID id,
			@RequestBody PlatformCountryTaxRulePutRequest body, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformCountryTaxRuleRowDto row = taxRuleAdminService.update(id, body);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_COUNTRY_TAX_RULE_UPDATED,
				AuditResourceTypes.PLATFORM_COUNTRY_TAX_RULE, row.id().toString(), rid,
				Map.of("countryCode", row.countryCode(), "ruleCode", row.ruleCode()));
		return ApiResponse.of(Map.of("item", row), rid);
	}

	@PatchMapping("/{id}/activate")
	public ApiResponse<Map<String, PlatformCountryTaxRuleRowDto>> activate(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformCountryTaxRuleRowDto row = taxRuleAdminService.activate(id);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_COUNTRY_TAX_RULE_ACTIVATED,
				AuditResourceTypes.PLATFORM_COUNTRY_TAX_RULE, row.id().toString(), rid,
				Map.of("countryCode", row.countryCode(), "ruleCode", row.ruleCode()));
		return ApiResponse.of(Map.of("item", row), rid);
	}

	@PatchMapping("/{id}/deactivate")
	public ApiResponse<Map<String, PlatformCountryTaxRuleRowDto>> deactivate(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformCountryTaxRuleRowDto row = taxRuleAdminService.deactivate(id);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_COUNTRY_TAX_RULE_DEACTIVATED,
				AuditResourceTypes.PLATFORM_COUNTRY_TAX_RULE, row.id().toString(), rid,
				Map.of("countryCode", row.countryCode(), "ruleCode", row.ruleCode()));
		return ApiResponse.of(Map.of("item", row), rid);
	}

	private UUID requirePlatformSuperadmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
		return userId;
	}
}
