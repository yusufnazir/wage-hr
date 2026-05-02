package com.wagepayroll.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import com.wagepayroll.api.dto.TenantPayPeriodItemDto;
import com.wagepayroll.api.dto.TenantPayPeriodRunCreateRequest;
import com.wagepayroll.api.dto.TenantPayPeriodRunItemDto;
import com.wagepayroll.api.dto.TenantPayPeriodStatusPatchRequest;
import com.wagepayroll.api.dto.TenantPayPeriodUpsertRequest;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.payperiod.TenantPayPeriodService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1")
public class TenantPayPeriodController {

	private final TenantPayPeriodService service;

	public TenantPayPeriodController(TenantPayPeriodService service) {
		this.service = service;
	}

	@GetMapping("/pay-periods")
	@RequiresPrivilege("PAY_PERIOD_VIEW")
	public ResponseEntity<ApiResponse<Object>> listPayPeriods(
			@RequestParam(name = "companyId", required = false) UUID companyId,
			@RequestParam(name = "year", required = false) Integer year,
			@RequestParam(name = "status", required = false) String status,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "sort", defaultValue = "startDate,desc") String sort) {
		Page<TenantPayPeriodItemDto> result = service.listPayPeriods(TenantContext.requireTenantId(), companyId, year,
				page, size, sort, status);
		return ResponseEntity.ok(ApiResponse.of(pagePayload(result), "tenant.pay_period.listed"));
	}

	@GetMapping("/pay-periods/{id}")
	@RequiresPrivilege("PAY_PERIOD_VIEW")
	public ResponseEntity<ApiResponse<Object>> getPayPeriod(@PathVariable("id") UUID id) {
		TenantPayPeriodItemDto item = service.getPayPeriod(TenantContext.requireTenantId(), id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.pay_period.fetched"));
	}

	@PostMapping("/pay-periods")
	@RequiresPrivilege("PAY_PERIOD_MANAGE")
	public ResponseEntity<ApiResponse<Object>> createPayPeriod(
			@Valid @RequestBody TenantPayPeriodUpsertRequest request) {
		TenantPayPeriodItemDto item = service.createPayPeriod(TenantContext.requireTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(Map.of("item", item), "tenant.pay_period.created"));
	}

	@PutMapping("/pay-periods/{id}")
	@RequiresPrivilege("PAY_PERIOD_MANAGE")
	public ResponseEntity<ApiResponse<Object>> updatePayPeriod(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantPayPeriodUpsertRequest request) {
		TenantPayPeriodItemDto item = service.updatePayPeriod(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.pay_period.updated"));
	}

	@PatchMapping("/pay-periods/{id}/status")
	@RequiresPrivilege("PAY_PERIOD_MANAGE")
	public ResponseEntity<ApiResponse<Object>> patchPayPeriodStatus(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantPayPeriodStatusPatchRequest request) {
		TenantPayPeriodItemDto item = service.patchPayPeriodStatus(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.pay_period.status.updated"));
	}

	@GetMapping("/pay-periods/{id}/runs")
	@RequiresPrivilege("PAY_PERIOD_RUN_VIEW")
	public ResponseEntity<ApiResponse<Object>> listRuns(@PathVariable("id") UUID id,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		Page<TenantPayPeriodRunItemDto> result = service.listRuns(TenantContext.requireTenantId(), id, page, size);
		return ResponseEntity.ok(ApiResponse.of(pagePayload(result), "tenant.pay_period_run.listed"));
	}

	@GetMapping("/pay-period-runs/{id}")
	@RequiresPrivilege("PAY_PERIOD_RUN_VIEW")
	public ResponseEntity<ApiResponse<Object>> getRun(@PathVariable("id") UUID id) {
		TenantPayPeriodRunItemDto item = service.getRun(TenantContext.requireTenantId(), id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.pay_period_run.fetched"));
	}

	@PostMapping("/pay-period-runs")
	@RequiresPrivilege("PAY_PERIOD_RUN_MANAGE")
	public ResponseEntity<ApiResponse<Object>> createRun(
			@Valid @RequestBody TenantPayPeriodRunCreateRequest request) {
		TenantPayPeriodRunItemDto item = service.createRun(TenantContext.requireTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(Map.of("item", item), "tenant.pay_period_run.created"));
	}

	private Map<String, Object> pagePayload(Page<?> pageResult) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("data", pageResult.getContent());
		payload.put("page", Map.of("number", pageResult.getNumber(), "size", pageResult.getSize(), "totalElements",
				pageResult.getTotalElements(), "totalPages", pageResult.getTotalPages()));
		return payload;
	}
}
