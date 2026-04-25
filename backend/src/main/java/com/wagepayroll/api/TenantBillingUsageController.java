package com.wagepayroll.api;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.SubmitUsageEventRequest;
import com.wagepayroll.billing.BillingMetricKey;
import com.wagepayroll.billing.BillingUsageAggregationService;
import com.wagepayroll.billing.BillingUsageEventService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/tenant/billing")
public class TenantBillingUsageController {

	private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

	private final BillingUsageEventService billingUsageEventService;
	private final BillingUsageAggregationService billingUsageAggregationService;

	public TenantBillingUsageController(BillingUsageEventService billingUsageEventService,
			BillingUsageAggregationService billingUsageAggregationService) {
		this.billingUsageEventService = billingUsageEventService;
		this.billingUsageAggregationService = billingUsageAggregationService;
	}

	/**
	 * Read-only daily usage aggregates for the current tenant (UTC day buckets). Optional filters:
	 * {@code metricKey}, {@code periodStart} / {@code periodEnd} (ISO-8601 dates, inclusive of whole UTC days on
	 * {@code period_start}). Defaults to the last 30 UTC days when dates are omitted.
	 */
	@GetMapping("/usage-aggregates")
	@RequiresPrivilege("TENANT_SETTINGS_EDIT")
	public ResponseEntity<ApiResponse<Map<String, Object>>> listUsageAggregates(@RequestParam(required = false) String metricKey,
			@RequestParam(required = false) String periodStart, @RequestParam(required = false) String periodEnd,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		BillingMetricKey metric = null;
		if (StringUtils.hasText(metricKey)) {
			metric = BillingMetricKey.parse(metricKey);
		}
		LocalDate ps = parseOptionalIsoDate("periodStart", periodStart);
		LocalDate pe = parseOptionalIsoDate("periodEnd", periodEnd);
		List<Map<String, Object>> aggregates = billingUsageAggregationService.listAggregatesForTenant(tenantId, metric, ps, pe);
		return ResponseEntity.ok(ApiResponse.of(Map.of("aggregates", aggregates), RequestIdFilter.currentRequestId(request)));
	}

	private static LocalDate parseOptionalIsoDate(String paramName, String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		try {
			return LocalDate.parse(raw.trim(), ISO_DATE);
		}
		catch (DateTimeParseException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_AGGREGATE_" + paramName.toUpperCase() + "_INVALID");
		}
	}

	/**
	 * Records a usage line for PAYG / metering. Idempotent per {@code (tenant_id, idempotency_key)}. Provider push
	 * (Stripe/PayPal usage APIs) is not performed in this v1 slice — persistence only.
	 */
	@PostMapping("/usage-events")
	@RequiresPrivilege("TENANT_SETTINGS_EDIT")
	public ResponseEntity<ApiResponse<Map<String, Object>>> submitUsageEvent(@Valid @RequestBody SubmitUsageEventRequest body,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		BillingMetricKey metric = BillingMetricKey.parse(body.metricKey());
		BillingUsageEventService.SubmitOutcome outcome = billingUsageEventService.trySubmit(tenantId, metric, body.quantity(),
				body.idempotencyKey());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("received", true);
		data.put("duplicate", outcome == BillingUsageEventService.SubmitOutcome.DUPLICATE);
		return ResponseEntity.ok(ApiResponse.of(data, RequestIdFilter.currentRequestId(request)));
	}
}
