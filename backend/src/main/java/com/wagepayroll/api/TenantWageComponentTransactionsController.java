package com.wagepayroll.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.TenantWageComponentTransactionPutRequest;
import com.wagepayroll.api.dto.TenantWageComponentTransactionRowDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.payrollstanding.TenantPayrollPeriodInputService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wage-component-transactions")
public class TenantWageComponentTransactionsController {

	private final TenantPayrollPeriodInputService service;

	public TenantWageComponentTransactionsController(TenantPayrollPeriodInputService service) {
		this.service = service;
	}

	@GetMapping
	@RequiresPrivilege("EMPLOYEE_PAYROLL_STANDING_VIEW")
	public ApiResponse<Map<String, Object>> list(@RequestParam(name = "companyId") UUID companyId,
			@RequestParam(name = "payPeriodId") UUID payPeriodId,
			@RequestParam(name = "employeeId", required = false) UUID employeeId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Map<String, Object> payload = service.listPeriodTransactions(tenantId, companyId, payPeriodId, employeeId, page,
				size);
		Map<String, Object> wrapped = new LinkedHashMap<>();
		wrapped.put("data", payload.get("items"));
		@SuppressWarnings("unchecked")
		long total = ((Number) payload.get("totalElements")).longValue();
		int p = (int) payload.get("page");
		int s = (int) payload.get("size");
		int totalPages = (int) payload.get("totalPages");
		wrapped.put("page", Map.of("number", p, "size", s, "totalElements", total, "totalPages", totalPages));
		return ApiResponse.of(wrapped, RequestIdFilter.currentRequestId(request));
	}

	@PutMapping("/{id}")
	@RequiresPrivilege("EMPLOYEE_PAYROLL_STANDING_MANAGE")
	public ApiResponse<Map<String, TenantWageComponentTransactionRowDto>> put(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantWageComponentTransactionPutRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantWageComponentTransactionRowDto row = service.putPeriodTransaction(tenantId, id, body, actorUserId(),
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request));
	}

	private static UUID actorUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(auth.getName());
	}
}
