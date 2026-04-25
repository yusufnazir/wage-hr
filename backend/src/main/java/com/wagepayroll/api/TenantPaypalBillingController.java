package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.CreatePaypalSubscriptionRequest;
import com.wagepayroll.billing.BillingPaypalSubscriptionService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/billing/paypal")
public class TenantPaypalBillingController {

	private final BillingPaypalSubscriptionService billingPaypalSubscriptionService;

	public TenantPaypalBillingController(BillingPaypalSubscriptionService billingPaypalSubscriptionService) {
		this.billingPaypalSubscriptionService = billingPaypalSubscriptionService;
	}

	/**
	 * Starts a PayPal billing subscription (plan must exist in the PayPal account). Returns the browser approval URL.
	 */
	@PostMapping("/subscription")
	@RequiresPrivilege("TENANT_SETTINGS_EDIT")
	public ResponseEntity<ApiResponse<Map<String, String>>> createSubscription(@Valid @RequestBody CreatePaypalSubscriptionRequest body,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		String url = billingPaypalSubscriptionService.createSubscriptionApprovalUrl(tenantId, body.planId(), body.returnUrl(),
				body.cancelUrl(), body.commercialPlanId());
		return ResponseEntity.ok(ApiResponse.of(Map.of("approvalUrl", url), RequestIdFilter.currentRequestId(request)));
	}
}
