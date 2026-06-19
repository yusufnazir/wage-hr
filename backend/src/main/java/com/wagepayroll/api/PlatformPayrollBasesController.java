package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PlatformPayrollBaseCreateRequest;
import com.wagepayroll.api.dto.PlatformPayrollBasePutRequest;
import com.wagepayroll.api.dto.PlatformPayrollBaseRowDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.payrollbase.PlatformPayrollBaseAdminService;
import com.wagepayroll.security.PlatformOperatorService;

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
@RequestMapping("/api/v1/platform/payroll-bases")
public class PlatformPayrollBasesController {

	private final PlatformOperatorService platformOperatorService;

	private final PlatformPayrollBaseAdminService payrollBaseAdminService;

	private final AuditService auditService;

	public PlatformPayrollBasesController(PlatformOperatorService platformOperatorService,
			PlatformPayrollBaseAdminService payrollBaseAdminService, AuditService auditService) {
		this.platformOperatorService = platformOperatorService;
		this.payrollBaseAdminService = payrollBaseAdminService;
		this.auditService = auditService;
	}

	@GetMapping
	public ApiResponse<Map<String, Object>> list(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "50") int size,
			@RequestParam(name = "category", required = false) String category,
			@RequestParam(name = "active", required = false) Boolean active,
			@RequestParam(name = "search", required = false) String search,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(payrollBaseAdminService.list(page, size, category, active, search), rid);
	}

	@GetMapping("/{id}")
	public ApiResponse<Map<String, PlatformPayrollBaseRowDto>> get(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		PlatformPayrollBaseRowDto row = payrollBaseAdminService.get(id);
		return ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, PlatformPayrollBaseRowDto>>> create(
			@RequestBody PlatformPayrollBaseCreateRequest body, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformPayrollBaseRowDto row = payrollBaseAdminService.create(body);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_PAYROLL_BASE_CREATED,
				AuditResourceTypes.PLATFORM_PAYROLL_BASE, row.id().toString(), rid, Map.of("code", row.code()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("item", row), rid));
	}

	@PutMapping("/{id}")
	public ApiResponse<Map<String, PlatformPayrollBaseRowDto>> put(@PathVariable("id") UUID id,
			@RequestBody PlatformPayrollBasePutRequest body, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformPayrollBaseRowDto row = payrollBaseAdminService.update(id, body);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_PAYROLL_BASE_UPDATED,
				AuditResourceTypes.PLATFORM_PAYROLL_BASE, row.id().toString(), rid, Map.of("code", row.code()));
		return ApiResponse.of(Map.of("item", row), rid);
	}

	@PatchMapping("/{id}/activate")
	public ApiResponse<Map<String, PlatformPayrollBaseRowDto>> activate(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformPayrollBaseRowDto row = payrollBaseAdminService.activate(id);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_PAYROLL_BASE_ACTIVATED,
				AuditResourceTypes.PLATFORM_PAYROLL_BASE, row.id().toString(), rid, Map.of("code", row.code()));
		return ApiResponse.of(Map.of("item", row), rid);
	}

	@PatchMapping("/{id}/deactivate")
	public ApiResponse<Map<String, PlatformPayrollBaseRowDto>> deactivate(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformPayrollBaseRowDto row = payrollBaseAdminService.deactivate(id);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_PAYROLL_BASE_DEACTIVATED,
				AuditResourceTypes.PLATFORM_PAYROLL_BASE, row.id().toString(), rid, Map.of("code", row.code()));
		return ApiResponse.of(Map.of("item", row), rid);
	}

	private UUID requirePlatformSuperadmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
		return userId;
	}
}
