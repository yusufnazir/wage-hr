package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantEmployeeCompensationDto;
import com.wagepayroll.api.dto.TenantEmployeeCompensationPutRequest;
import com.wagepayroll.api.dto.TenantPayrollYtdAccumulatorRowDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.compensation.TenantEmployeeCompensationService;
import com.wagepayroll.payroll.TenantEmployeePayrollYtdService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TenantEmployeeCompensationController {

	private final TenantEmployeeCompensationService service;
	private final TenantEmployeePayrollYtdService payrollYtdService;

	public TenantEmployeeCompensationController(TenantEmployeeCompensationService service,
			TenantEmployeePayrollYtdService payrollYtdService) {
		this.service = service;
		this.payrollYtdService = payrollYtdService;
	}

	@GetMapping("/employees/{id}/compensation")
	@RequiresPrivilege("EMPLOYEE_VIEW")
	public ResponseEntity<ApiResponse<Object>> getCompensation(@PathVariable("id") UUID employeeId) {
		TenantEmployeeCompensationDto item = service.getForEmployee(TenantContext.requireTenantId(), employeeId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.employee.compensation.fetched"));
	}

	@PutMapping("/employees/{id}/compensation")
	@RequiresPrivilege("EMPLOYEE_MANAGE")
	public ResponseEntity<ApiResponse<Object>> putCompensation(@PathVariable("id") UUID employeeId,
			@RequestBody TenantEmployeeCompensationPutRequest request) {
		TenantEmployeeCompensationDto item = service.putForEmployee(TenantContext.requireTenantId(), employeeId, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.employee.compensation.updated"));
	}

	@GetMapping("/employees/{id}/payroll-ytd")
	@RequiresPrivilege("PAY_PERIOD_VIEW")
	public ResponseEntity<ApiResponse<Object>> listPayrollYtd(@PathVariable("id") UUID employeeId,
			@RequestParam(name = "taxYear") int taxYear) {
		List<TenantPayrollYtdAccumulatorRowDto> data = payrollYtdService
				.listForEmployee(TenantContext.requireTenantId(), employeeId, taxYear);
		return ResponseEntity.ok(ApiResponse.of(Map.of("data", data), "tenant.employee.payroll_ytd.listed"));
	}
}
