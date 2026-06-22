package com.wagepayroll.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import com.wagepayroll.api.dto.EvaluatedComponentAmountDto;
import com.wagepayroll.api.dto.TenantMaterializePayrollInputsRequest;
import com.wagepayroll.api.dto.TenantMaterializePayrollInputsResultDto;
import com.wagepayroll.api.dto.TenantPayPeriodFinalizeRequest;
import com.wagepayroll.api.dto.TenantPayPeriodFinalizeResultDto;
import com.wagepayroll.api.dto.TenantPayPeriodFormulaPreviewRequest;
import com.wagepayroll.api.dto.TenantPayPeriodItemDto;
import com.wagepayroll.api.dto.TenantPayPeriodRunCreateRequest;
import com.wagepayroll.api.dto.TenantPayPeriodRunItemDto;
import com.wagepayroll.api.dto.TenantPayrollResultLineRowDto;
import com.wagepayroll.api.dto.TenantPayPeriodStatusPatchRequest;
import com.wagepayroll.api.dto.TenantPayPeriodUpsertRequest;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.payperiod.TenantPayPeriodService;
import com.wagepayroll.payrollstanding.TenantPayrollPeriodInputService;
import com.wagepayroll.payroll.TenantPayrollFinalizeService;
import com.wagepayroll.payroll.TenantPayrollFormulaPreviewService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
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
	private final TenantPayrollPeriodInputService payrollPeriodInputService;
	private final TenantPayrollFormulaPreviewService payrollFormulaPreviewService;
	private final TenantPayrollFinalizeService payrollFinalizeService;

	public TenantPayPeriodController(TenantPayPeriodService service,
			TenantPayrollPeriodInputService payrollPeriodInputService,
			TenantPayrollFormulaPreviewService payrollFormulaPreviewService,
			TenantPayrollFinalizeService payrollFinalizeService) {
		this.service = service;
		this.payrollPeriodInputService = payrollPeriodInputService;
		this.payrollFormulaPreviewService = payrollFormulaPreviewService;
		this.payrollFinalizeService = payrollFinalizeService;
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

	@PostMapping("/pay-periods/{id}/supervisor-approve")
	@RequiresPrivilege("PAY_PERIOD_SUPERVISOR_APPROVE")
	public ResponseEntity<ApiResponse<Object>> supervisorApprove(@PathVariable("id") UUID id,
			HttpServletRequest httpRequest) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
		TenantPayPeriodItemDto item = service.supervisorApprove(tenantId, id, actor,
				RequestIdFilter.currentRequestId(httpRequest));
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.pay_period.supervisor_approved"));
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

	@PostMapping("/pay-periods/{periodId}/runs/{runId}/finalize")
	@RequiresPrivilege("PAY_PERIOD_RUN_MANAGE")
	public ResponseEntity<ApiResponse<Object>> finalizeRun(@PathVariable("periodId") UUID payPeriodId,
			@PathVariable("runId") UUID runId, @RequestBody(required = false) TenantPayPeriodFinalizeRequest request,
			HttpServletRequest httpRequest) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
		TenantPayPeriodFinalizeResultDto result = payrollFinalizeService.finalize(tenantId, payPeriodId, runId, request,
				actor, RequestIdFilter.currentRequestId(httpRequest));
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", result), "tenant.pay_period_run.finalized"));
	}

	@GetMapping("/pay-period-runs/{runId}/result-lines")
	@RequiresPrivilege("PAY_PERIOD_RUN_VIEW")
	public ResponseEntity<ApiResponse<Object>> listResultLines(@PathVariable("runId") UUID runId,
			@RequestParam(name = "employeeId", required = false) UUID employeeId) {
		List<TenantPayrollResultLineRowDto> lines = payrollFinalizeService
				.listResultLines(TenantContext.requireTenantId(), runId, employeeId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("data", lines), "tenant.payroll_result_line.listed"));
	}

	@PostMapping("/pay-periods/{id}/formula-preview")
	@RequiresPrivilege("PAY_PERIOD_VIEW")
	public ResponseEntity<ApiResponse<Object>> formulaPreview(@PathVariable("id") UUID payPeriodId,
			@Valid @RequestBody TenantPayPeriodFormulaPreviewRequest request, HttpServletRequest httpRequest) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
		boolean persist = Boolean.TRUE.equals(request.persistToPeriodInputs());
		var preview = payrollFormulaPreviewService.preview(tenantId, payPeriodId, request.employeeIds(), persist, actor,
				RequestIdFilter.currentRequestId(httpRequest));
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("items", preview.items());
		payload.put("employeeBaseTotals", preview.employeeBaseTotals());
		payload.put("employeeNetPay", preview.employeeNetPay());
		payload.put("employeeArt17AttributionPeriods", preview.employeeArt17AttributionPeriods());
		payload.put("employeeCalculationTraceLines", preview.employeeCalculationTraceLines());
		payload.put("employeeCalculationTraceText", preview.employeeCalculationTraceText());
		return ResponseEntity.ok(ApiResponse.of(payload, "tenant.pay_period.formula_preview"));
	}

	@GetMapping("/pay-periods/{payPeriodId}/employees/{employeeId}/calculation-trace.txt")
	@RequiresPrivilege("PAY_PERIOD_VIEW")
	public ResponseEntity<String> downloadCalculationTrace(@PathVariable("payPeriodId") UUID payPeriodId,
			@PathVariable("employeeId") UUID employeeId) {
		UUID tenantId = TenantContext.requireTenantId();
		String body = payrollFormulaPreviewService.renderTraceDownload(tenantId, payPeriodId, employeeId);
		if (body == null || body.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CALCULATION_TRACE_NOT_FOUND");
		}
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"payroll-calculation-" + employeeId + ".txt\"")
				.header("Content-Type", "text/plain; charset=UTF-8")
				.body(body);
	}

	/**
	 * Materializes active standing instructions into {@code tenant_wage_component_transaction} for the pay period
	 * (explicit “prepare period” trigger per module doc).
	 */
	@PostMapping("/pay-periods/{id}/materialize-payroll-inputs")
	@RequiresPrivilege("EMPLOYEE_PAYROLL_STANDING_MANAGE")
	public ApiResponse<Map<String, TenantMaterializePayrollInputsResultDto>> materializePayrollInputs(
			@PathVariable("id") UUID payPeriodId, @Valid @RequestBody TenantMaterializePayrollInputsRequest request,
			HttpServletRequest httpRequest) {
		if (request.companyId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyId is required");
		}
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
		TenantMaterializePayrollInputsResultDto result = payrollPeriodInputService.materializeForPayPeriod(tenantId,
				request.companyId(), payPeriodId, request.employeeIds(), actor,
				RequestIdFilter.currentRequestId(httpRequest));
		return ApiResponse.of(Map.of("item", result), RequestIdFilter.currentRequestId(httpRequest));
	}

	private Map<String, Object> pagePayload(Page<?> pageResult) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("data", pageResult.getContent());
		payload.put("page", Map.of("number", pageResult.getNumber(), "size", pageResult.getSize(), "totalElements",
				pageResult.getTotalElements(), "totalPages", pageResult.getTotalPages()));
		return payload;
	}
}
