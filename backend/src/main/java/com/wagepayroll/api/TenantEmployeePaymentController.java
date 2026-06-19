package com.wagepayroll.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.wagepayroll.api.dto.TenantEmployeePayPeriodPaymentRowDto;
import com.wagepayroll.api.dto.TenantEmployeePaymentDestinationPutItem;
import com.wagepayroll.api.dto.TenantEmployeePaymentDestinationRowDto;
import com.wagepayroll.api.dto.TenantEmployeePaymentOverviewDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.employeepayment.TenantEmployeePaymentService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.data.domain.Page;
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
public class TenantEmployeePaymentController {

	private final TenantEmployeePaymentService service;

	public TenantEmployeePaymentController(TenantEmployeePaymentService service) {
		this.service = service;
	}

	@GetMapping("/employees/{id}/payment-overview")
	@RequiresPrivilege("EMPLOYEE_PAYMENT_VIEW")
	public ResponseEntity<ApiResponse<Object>> paymentOverview(@PathVariable("id") UUID employeeId) {
		TenantEmployeePaymentOverviewDto overview = service.overview(TenantContext.requireTenantId(), employeeId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", overview), "tenant.employee_payment.overview"));
	}

	@GetMapping("/employees/{id}/payment-history")
	@RequiresPrivilege("EMPLOYEE_PAYMENT_VIEW")
	public ResponseEntity<ApiResponse<Object>> paymentHistory(@PathVariable("id") UUID employeeId,
			@RequestParam(name = "year", required = false) Integer year,
			@RequestParam(name = "payPeriodId", required = false) UUID payPeriodId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "50") int size) {
		Page<TenantEmployeePayPeriodPaymentRowDto> result = service.listPaymentHistory(
				TenantContext.requireTenantId(), employeeId, year, payPeriodId, page, size);
		return ResponseEntity.ok(ApiResponse.of(pagePayload(result), "tenant.employee_payment.history.listed"));
	}

	@PutMapping("/employees/{id}/payment-destinations")
	@RequiresPrivilege("EMPLOYEE_PAYMENT_MANAGE")
	public ResponseEntity<ApiResponse<Object>> replaceDestinations(@PathVariable("id") UUID employeeId,
			@Valid @RequestBody ReplaceDestinationsRequest request) {
		List<TenantEmployeePaymentDestinationRowDto> items = service.replaceDestinations(
				TenantContext.requireTenantId(), employeeId, request.items());
		return ResponseEntity.ok(ApiResponse.of(Map.of("data", items), "tenant.employee_payment.destinations.updated"));
	}

	public record ReplaceDestinationsRequest(@NotNull List<TenantEmployeePaymentDestinationPutItem> items) {
	}

	private Map<String, Object> pagePayload(Page<?> pageResult) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("data", pageResult.getContent());
		payload.put("page", Map.of("number", pageResult.getNumber(), "size", pageResult.getSize(), "totalElements",
				pageResult.getTotalElements(), "totalPages", pageResult.getTotalPages()));
		return payload;
	}
}
