package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.CreateStripeBillingPortalSessionRequest;
import com.wagepayroll.api.dto.CreateStripeCheckoutSessionRequest;
import com.wagepayroll.billing.BillingStripeHostedCheckoutService;
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
@RequestMapping("/api/v1/tenant/billing/stripe")
public class TenantStripeBillingController {

	private final BillingStripeHostedCheckoutService billingStripeHostedCheckoutService;

	public TenantStripeBillingController(BillingStripeHostedCheckoutService billingStripeHostedCheckoutService) {
		this.billingStripeHostedCheckoutService = billingStripeHostedCheckoutService;
	}

	@PostMapping("/checkout-session")
	@RequiresPrivilege("TENANT_SETTINGS_EDIT")
	public ResponseEntity<ApiResponse<Map<String, String>>> createCheckoutSession(@Valid @RequestBody CreateStripeCheckoutSessionRequest body,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		String url = billingStripeHostedCheckoutService.createSubscriptionCheckoutSession(tenantId, body.commercialPlanId(), body.priceId(),
				body.successUrl(), body.cancelUrl());
		return ResponseEntity.ok(ApiResponse.of(Map.of("url", url), RequestIdFilter.currentRequestId(request)));
	}

	@PostMapping("/billing-portal-session")
	@RequiresPrivilege("TENANT_SETTINGS_EDIT")
	public ResponseEntity<ApiResponse<Map<String, String>>> createBillingPortalSession(
			@Valid @RequestBody CreateStripeBillingPortalSessionRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		String url = billingStripeHostedCheckoutService.createBillingPortalSession(tenantId, body.returnUrl());
		return ResponseEntity.ok(ApiResponse.of(Map.of("url", url), RequestIdFilter.currentRequestId(request)));
	}
}
