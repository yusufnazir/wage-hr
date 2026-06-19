package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.TenantActivePatchRequest;
import com.wagepayroll.api.dto.TenantWageComponentCreateRequest;
import com.wagepayroll.api.dto.TenantWageComponentPutRequest;
import com.wagepayroll.api.dto.TenantWageComponentRowDto;
import com.wagepayroll.api.dto.WageComponentFormulaValidateRequest;
import com.wagepayroll.api.dto.WageComponentFormulaValidateResultDto;
import com.wagepayroll.payroll.formula.WageComponentFormulaValidateService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;
import com.wagepayroll.wagecomponent.TenantWageComponentService;

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
@RequestMapping("/api/v1/wage-components")
public class TenantWageComponentsController {

	private final TenantWageComponentService tenantWageComponentService;
	private final WageComponentFormulaValidateService formulaValidateService;

	public TenantWageComponentsController(TenantWageComponentService tenantWageComponentService,
			WageComponentFormulaValidateService formulaValidateService) {
		this.tenantWageComponentService = tenantWageComponentService;
		this.formulaValidateService = formulaValidateService;
	}

	@GetMapping
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ResponseEntity<ApiResponse<Object>> list(@RequestParam(name = "companyId", required = false) UUID companyId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "active", required = false) Boolean active,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Map<String, Object> payload = tenantWageComponentService.list(tenantId, companyId, page, size, active);
		return ResponseEntity.ok(ApiResponse.of(payload, RequestIdFilter.currentRequestId(request)));
	}

	@GetMapping("/catalog/templates")
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ResponseEntity<ApiResponse<Object>> catalogTemplates(@RequestParam(name = "companyId") UUID companyId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ResponseEntity.ok(ApiResponse.of(
				Map.of("items", tenantWageComponentService.listTemplatesForCompany(tenantId, companyId)),
				RequestIdFilter.currentRequestId(request)));
	}

	@GetMapping("/catalog/statutory")
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ResponseEntity<ApiResponse<Object>> catalogStatutory(@RequestParam(name = "companyId") UUID companyId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ResponseEntity.ok(ApiResponse.of(
				Map.of("items", tenantWageComponentService.listStatutoryForCompany(tenantId, companyId)),
				RequestIdFilter.currentRequestId(request)));
	}

	@GetMapping("/{id}")
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ResponseEntity<ApiResponse<Object>> get(@PathVariable("id") UUID id, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantWageComponentRowDto row = tenantWageComponentService.get(tenantId, id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request)));
	}

	@PostMapping
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	public ResponseEntity<ApiResponse<Object>> create(@Valid @RequestBody TenantWageComponentCreateRequest body,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		actorUserId();
		TenantWageComponentRowDto row = tenantWageComponentService.createFromTemplate(tenantId, body);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request)));
	}

	@PutMapping("/{id}")
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	public ResponseEntity<ApiResponse<Object>> put(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantWageComponentPutRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		actorUserId();
		TenantWageComponentRowDto row = tenantWageComponentService.update(tenantId, id, body);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request)));
	}

	@PostMapping("/validate-formula")
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ResponseEntity<ApiResponse<Object>> validateFormula(
			@Valid @RequestBody WageComponentFormulaValidateRequest body, HttpServletRequest request) {
		TenantContext.requireTenantId();
		WageComponentFormulaValidateResultDto result = formulaValidateService.validate(body);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", result), RequestIdFilter.currentRequestId(request)));
	}

	@PatchMapping("/{id}/active")
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	public ResponseEntity<ApiResponse<Object>> patchActive(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantActivePatchRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		actorUserId();
		if (body.active() == null) {
			return ResponseEntity.badRequest().build();
		}
		TenantWageComponentRowDto row = tenantWageComponentService.patchActive(tenantId, id, body.active().booleanValue());
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request)));
	}

	private static UUID actorUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(auth.getName());
	}
}
