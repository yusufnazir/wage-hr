package com.wagepayroll.billing;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingStripeWebhookService {

	private static final Logger log = LoggerFactory.getLogger(BillingStripeWebhookService.class);

	private final BillingWebhookReceiptService billingWebhookReceiptService;
	private final StripeTenantResolverV1 stripeTenantResolverV1;
	private final BillingStripeSubscriptionWebhookReconciler billingStripeSubscriptionWebhookReconciler;

	@Value("${app.billing.stripe.webhook-secret:}")
	private String webhookSecret;

	public BillingStripeWebhookService(BillingWebhookReceiptService billingWebhookReceiptService,
			StripeTenantResolverV1 stripeTenantResolverV1,
			BillingStripeSubscriptionWebhookReconciler billingStripeSubscriptionWebhookReconciler) {
		this.billingWebhookReceiptService = billingWebhookReceiptService;
		this.stripeTenantResolverV1 = stripeTenantResolverV1;
		this.billingStripeSubscriptionWebhookReconciler = billingStripeSubscriptionWebhookReconciler;
	}

	public Map<String, Object> handle(String rawBody, String stripeSignatureHeader) {
		if (!StringUtils.hasText(webhookSecret)) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STRIPE_WEBHOOK_NOT_CONFIGURED");
		}
		if (!StringUtils.hasText(stripeSignatureHeader)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STRIPE_SIGNATURE_REQUIRED");
		}
		final Event event;
		try {
			event = Webhook.constructEvent(rawBody, stripeSignatureHeader, webhookSecret);
		}
		catch (SignatureVerificationException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STRIPE_SIGNATURE");
		}
		if (event.getId() == null || event.getId().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STRIPE_EVENT_ID_MISSING");
		}
		Instant now = Instant.now();
		TenantResolutionResult resolution = stripeTenantResolverV1.resolve(rawBody);
		String eventType = event.getType() != null ? event.getType() : "";
		WebhookReceiptInsert insert = new WebhookReceiptInsert(BillingWebhookProvider.STRIPE, event.getId(), now, rawBody, eventType,
				resolution.state(), resolution.tenantId(), resolution.reasonCode(), resolution.missingFieldPath(),
				resolution.resolverVersion());
		BillingWebhookReceiptService.InsertOutcome outcome = billingWebhookReceiptService.tryInsertReceipt(insert);
		log.info(
				"event=billing_webhook.stripe_recorded provider=STRIPE providerEventId={} resolutionState={} reasonCode={} missingFieldPath={} resolverVersion={} duplicate={}",
				insert.providerEventId(), resolution.state(), nullToDash(resolution.reasonCode()),
				nullToDash(resolution.missingFieldPath()), nullToDash(resolution.resolverVersion()),
				outcome == BillingWebhookReceiptService.InsertOutcome.DUPLICATE);
		billingStripeSubscriptionWebhookReconciler.maybeReconcileAfterReceipt(event, resolution, outcome);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("received", true);
		body.put("duplicate", outcome == BillingWebhookReceiptService.InsertOutcome.DUPLICATE);
		body.put("tenantResolutionState", resolution.state().name());
		body.put("tenantResolutionReasonCode", resolution.reasonCode());
		body.put("tenantResolutionMissingFieldPath", resolution.missingFieldPath());
		body.put("tenantResolutionResolverVersion", resolution.resolverVersion());
		return body;
	}

	private static String nullToDash(String v) {
		return v == null ? "-" : v;
	}
}
