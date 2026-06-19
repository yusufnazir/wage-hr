package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.TenantPayrollStandingInstructionCreateRequest;
import com.wagepayroll.api.dto.TenantPayrollStandingInstructionPatchRequest;
import com.wagepayroll.api.dto.TenantPayrollStandingInstructionPutRequest;
import com.wagepayroll.api.dto.TenantPayrollStandingInstructionRowDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.payrollstanding.TenantPayrollPeriodInputService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

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
@RequestMapping("/api/v1/payroll-standing-instructions")
public class TenantPayrollStandingInstructionsController {

	private final TenantPayrollPeriodInputService service;

	public TenantPayrollStandingInstructionsController(TenantPayrollPeriodInputService service) {
		this.service = service;
	}

	@GetMapping
	@RequiresPrivilege("EMPLOYEE_PAYROLL_STANDING_VIEW")
	public ApiResponse<Map<String, Object>> list(@RequestParam(name = "companyId") UUID companyId,
			@RequestParam(name = "employeeId") UUID employeeId, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ApiResponse.of(service.listStandingInstructions(tenantId, companyId, employeeId),
				RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{id}")
	@RequiresPrivilege("EMPLOYEE_PAYROLL_STANDING_VIEW")
	public ApiResponse<Map<String, TenantPayrollStandingInstructionRowDto>> get(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantPayrollStandingInstructionRowDto row = service.getStandingInstruction(tenantId, id);
		return ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	@RequiresPrivilege("EMPLOYEE_PAYROLL_STANDING_MANAGE")
	public ResponseEntity<ApiResponse<Map<String, TenantPayrollStandingInstructionRowDto>>> create(
			@Valid @RequestBody TenantPayrollStandingInstructionCreateRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantPayrollStandingInstructionRowDto row = service.createStandingInstruction(tenantId, body, actorUserId(),
				RequestIdFilter.currentRequestId(request));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request)));
	}

	@PutMapping("/{id}")
	@RequiresPrivilege("EMPLOYEE_PAYROLL_STANDING_MANAGE")
	public ApiResponse<Map<String, TenantPayrollStandingInstructionRowDto>> put(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantPayrollStandingInstructionPutRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantPayrollStandingInstructionRowDto row = service.putStandingInstruction(tenantId, id, body, actorUserId(),
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request));
	}

	@PatchMapping("/{id}")
	@RequiresPrivilege("EMPLOYEE_PAYROLL_STANDING_MANAGE")
	public ApiResponse<Map<String, TenantPayrollStandingInstructionRowDto>> patch(@PathVariable("id") UUID id,
			@RequestBody TenantPayrollStandingInstructionPatchRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantPayrollStandingInstructionRowDto row = service.patchStandingInstruction(tenantId, id, body, actorUserId(),
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request));
	}

	private static UUID actorUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(auth.getName());
	}
}
