package com.wagepayroll.api;

import java.util.Map;

import com.wagepayroll.billing.BillingPaypalWebhookService;
import com.wagepayroll.billing.BillingStripeWebhookService;
import com.wagepayroll.billing.PayPalWebhookIngress;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/webhooks")
public class BillingWebhooksController {

	private final BillingStripeWebhookService billingStripeWebhookService;
	private final BillingPaypalWebhookService billingPaypalWebhookService;

	public BillingWebhooksController(BillingStripeWebhookService billingStripeWebhookService,
			BillingPaypalWebhookService billingPaypalWebhookService) {
		this.billingStripeWebhookService = billingStripeWebhookService;
		this.billingPaypalWebhookService = billingPaypalWebhookService;
	}

	@PostMapping(value = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> stripe(@RequestBody String rawBody,
			@RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {
		return ResponseEntity.ok(billingStripeWebhookService.handle(rawBody, stripeSignature));
	}

	@PostMapping(value = "/paypal", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> paypal(@RequestBody String rawBody, HttpServletRequest request) {
		return ResponseEntity.ok(billingPaypalWebhookService.handle(PayPalWebhookIngress.from(request, rawBody)));
	}
}
