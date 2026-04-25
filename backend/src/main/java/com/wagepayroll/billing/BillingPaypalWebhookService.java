package com.wagepayroll.billing;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingPaypalWebhookService {

	private static final Logger log = LoggerFactory.getLogger(BillingPaypalWebhookService.class);

	private final ObjectMapper objectMapper;
	private final BillingWebhookReceiptService billingWebhookReceiptService;
	private final PayPalWebhookSignatureClient payPalWebhookSignatureClient;
	private final PayPalTenantResolverV1 payPalTenantResolverV1;
	private final BillingPaypalSubscriptionWebhookReconciler billingPaypalSubscriptionWebhookReconciler;

	@Value("${app.billing.paypal.webhook-id:}")
	private String webhookId;

	@Value("${app.billing.paypal.verify-signature:false}")
	private boolean verifySignature;

	public BillingPaypalWebhookService(ObjectMapper objectMapper, BillingWebhookReceiptService billingWebhookReceiptService,
			PayPalWebhookSignatureClient payPalWebhookSignatureClient, PayPalTenantResolverV1 payPalTenantResolverV1,
			BillingPaypalSubscriptionWebhookReconciler billingPaypalSubscriptionWebhookReconciler) {
		this.objectMapper = objectMapper;
		this.billingWebhookReceiptService = billingWebhookReceiptService;
		this.payPalWebhookSignatureClient = payPalWebhookSignatureClient;
		this.payPalTenantResolverV1 = payPalTenantResolverV1;
		this.billingPaypalSubscriptionWebhookReconciler = billingPaypalSubscriptionWebhookReconciler;
	}

	public Map<String, Object> handle(PayPalWebhookIngress ingress) {
		if (!StringUtils.hasText(webhookId)) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PAYPAL_WEBHOOK_NOT_CONFIGURED");
		}
		if (!StringUtils.hasText(ingress.transmissionId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYPAL_TRANSMISSION_ID_REQUIRED");
		}
		if (ingress.transmissionId().length() > 255) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYPAL_TRANSMISSION_ID_TOO_LONG");
		}
		final JsonNode root;
		try {
			root = objectMapper.readTree(ingress.rawBody() == null ? "" : ingress.rawBody());
		}
		catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAYPAL_JSON");
		}
		if (!root.isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAYPAL_JSON");
		}
		if (verifySignature) {
			payPalWebhookSignatureClient.verifySignatureOrThrow(ingress, webhookId, root);
		}
		JsonNode wid = root.get("webhook_id");
		if (wid != null && wid.isTextual() && !webhookId.equals(wid.asText())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYPAL_WEBHOOK_ID_MISMATCH");
		}
		JsonNode eventTypeNode = root.get("event_type");
		if (eventTypeNode == null || !eventTypeNode.isTextual() || eventTypeNode.asText().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYPAL_EVENT_TYPE_REQUIRED");
		}
		String eventType = eventTypeNode.asText().trim();
		Instant now = Instant.now();
		String rawPayload = ingress.rawBody() == null ? "" : ingress.rawBody();
		TenantResolutionResult resolution = payPalTenantResolverV1.resolve(root);
		WebhookReceiptInsert insert = new WebhookReceiptInsert(BillingWebhookProvider.PAYPAL, ingress.transmissionId().trim(), now,
				rawPayload, eventType, resolution.state(), resolution.tenantId(), resolution.reasonCode(), resolution.missingFieldPath(),
				resolution.resolverVersion());
		BillingWebhookReceiptService.InsertOutcome outcome = billingWebhookReceiptService.tryInsertReceipt(insert);
		billingPaypalSubscriptionWebhookReconciler.maybeReconcileAfterReceipt(root, ingress.transmissionId().trim(), resolution, outcome);
		log.info(
				"event=billing_webhook.paypal_recorded provider=PAYPAL providerEventId={} resolutionState={} reasonCode={} missingFieldPath={} resolverVersion={} duplicate={}",
				insert.providerEventId(), resolution.state(), nullToDash(resolution.reasonCode()),
				nullToDash(resolution.missingFieldPath()), nullToDash(resolution.resolverVersion()),
				outcome == BillingWebhookReceiptService.InsertOutcome.DUPLICATE);
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
