package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.PlatformWageComponentTemplateCreateRequest;
import com.wagepayroll.api.dto.PlatformWageComponentTemplatePutLedgerRequest;
import com.wagepayroll.api.dto.PlatformWageComponentTemplatePutRequest;
import com.wagepayroll.api.dto.PlatformWageComponentTemplateRowDto;
import com.wagepayroll.api.dto.WageComponentFormulaValidateRequest;
import com.wagepayroll.api.dto.WageComponentFormulaValidateResultDto;
import com.wagepayroll.payroll.formula.WageComponentFormulaValidateService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.PlatformOperatorService;
import com.wagepayroll.wagecomponent.PlatformWageComponentTemplateAdminService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/wage-component-templates")
public class PlatformWageComponentTemplatesController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformWageComponentTemplateAdminService adminService;
	private final WageComponentFormulaValidateService formulaValidateService;

	public PlatformWageComponentTemplatesController(PlatformOperatorService platformOperatorService,
			PlatformWageComponentTemplateAdminService adminService,
			WageComponentFormulaValidateService formulaValidateService) {
		this.platformOperatorService = platformOperatorService;
		this.adminService = adminService;
		this.formulaValidateService = formulaValidateService;
	}

	@GetMapping
	public ApiResponse<Map<String, Object>> list(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "active", required = false) Boolean active,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		return ApiResponse.of(adminService.list(page, size, country, active), RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{id}")
	public ApiResponse<Map<String, PlatformWageComponentTemplateRowDto>> get(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		return ApiResponse.of(Map.of("template", adminService.get(id)), RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, PlatformWageComponentTemplateRowDto>>> create(
			@Valid @RequestBody PlatformWageComponentTemplateCreateRequest body, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformWageComponentTemplateRowDto row = adminService.create(body, actor, RequestIdFilter.currentRequestId(request));
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("template", row), rid));
	}

	@PutMapping("/{id}")
	public ApiResponse<Map<String, PlatformWageComponentTemplateRowDto>> put(@PathVariable("id") UUID id,
			@Valid @RequestBody PlatformWageComponentTemplatePutRequest body, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformWageComponentTemplateRowDto row = adminService.update(id, body, actor, RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("template", row), RequestIdFilter.currentRequestId(request));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable("id") UUID id, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		adminService.delete(id, actor, RequestIdFilter.currentRequestId(request));
	}

	@PostMapping("/validate-formula")
	public ApiResponse<Map<String, WageComponentFormulaValidateResultDto>> validateFormula(
			@Valid @RequestBody WageComponentFormulaValidateRequest body, HttpServletRequest request) {
		requirePlatformSuperadmin();
		WageComponentFormulaValidateResultDto result = formulaValidateService.validate(body);
		return ApiResponse.of(Map.of("item", result), RequestIdFilter.currentRequestId(request));
	}

	@PutMapping("/{id}/ledger-links")
	public ApiResponse<Map<String, PlatformWageComponentTemplateRowDto>> putLedgerLinks(@PathVariable("id") UUID id,
			@Valid @RequestBody PlatformWageComponentTemplatePutLedgerRequest body, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformWageComponentTemplateRowDto row = adminService.putLedgerLinks(id, body, actor,
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("template", row), RequestIdFilter.currentRequestId(request));
	}

	private UUID requirePlatformSuperadmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
		return userId;
	}
}
