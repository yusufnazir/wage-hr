package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.CommercialPlanListItemDto;
import com.wagepayroll.api.dto.TenantBillingSummaryDto;
import com.wagepayroll.billing.BillingTenantSummaryService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.plans.CommercialPlanService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/billing")
public class TenantBillingSummaryController {

	private final BillingTenantSummaryService billingTenantSummaryService;
	private final CommercialPlanService commercialPlanService;

	public TenantBillingSummaryController(BillingTenantSummaryService billingTenantSummaryService,
			CommercialPlanService commercialPlanService) {
		this.billingTenantSummaryService = billingTenantSummaryService;
		this.commercialPlanService = commercialPlanService;
	}

	@GetMapping("/summary")
	@RequiresPrivilege("USER_VIEW")
	public ResponseEntity<ApiResponse<Map<String, TenantBillingSummaryDto>>> summary(HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantBillingSummaryDto dto = billingTenantSummaryService.summarize(tenantId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("summary", dto), RequestIdFilter.currentRequestId(request)));
	}

	@GetMapping("/commercial-plans")
	@RequiresPrivilege("TENANT_SETTINGS_EDIT")
	public ResponseEntity<ApiResponse<Map<String, List<CommercialPlanListItemDto>>>> commercialPlans(HttpServletRequest request) {
		TenantContext.requireTenantId();
		List<CommercialPlanListItemDto> plans = commercialPlanService.listActivePlansForTenantCatalog();
		return ResponseEntity.ok(ApiResponse.of(Map.of("plans", plans), RequestIdFilter.currentRequestId(request)));
	}
}
