package com.wagepayroll.billing;

import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.billing.BillingProviderLinkEntity;
import com.wagepayroll.domain.billing.BillingProviderLinkRepository;

import org.springframework.stereotype.Service;

/**
 * Versioned Stripe webhook → tenant resolution using {@code billing_provider_link} for provider {@code STRIPE}.
 *
 * <p>Customer id is read from {@code data.object} when present as {@code customer} (string or nested id) or when the
 * object itself is a customer.</p>
 */
@Service
public class StripeTenantResolverV1 {

	public static final String VERSION = "StripeTenantResolverV1";

	private final ObjectMapper objectMapper;
	private final BillingProviderLinkRepository billingProviderLinkRepository;

	public StripeTenantResolverV1(ObjectMapper objectMapper, BillingProviderLinkRepository billingProviderLinkRepository) {
		this.objectMapper = objectMapper;
		this.billingProviderLinkRepository = billingProviderLinkRepository;
	}

	public TenantResolutionResult resolve(String rawJson) {
		if (rawJson == null || rawJson.isBlank()) {
			return TenantResolutionResult.insufficientData("empty_body", null, VERSION);
		}
		final JsonNode root;
		try {
			root = objectMapper.readTree(rawJson);
		}
		catch (Exception ex) {
			return TenantResolutionResult.insufficientData("invalid_json", null, VERSION);
		}
		Optional<String> customerId = extractStripeCustomerId(root);
		if (customerId.isEmpty()) {
			return TenantResolutionResult.insufficientData("stripe_customer_missing", "data.object.customer", VERSION);
		}
		Optional<BillingProviderLinkEntity> link = billingProviderLinkRepository.findByProviderAndExternalCustomerId(
				BillingWebhookProvider.STRIPE, customerId.get());
		if (link.isEmpty()) {
			return TenantResolutionResult.noMatch("billing_provider_link_not_found", VERSION);
		}
		return TenantResolutionResult.resolved(link.get().getTenantId(), VERSION);
	}

	private static Optional<String> extractStripeCustomerId(JsonNode root) {
		JsonNode dataObject = root.path("data").path("object");
		if (dataObject.isMissingNode() || !dataObject.isObject()) {
			return Optional.empty();
		}
		JsonNode customer = dataObject.get("customer");
		if (customer != null && customer.isTextual()) {
			String id = customer.asText();
			return id.isBlank() ? Optional.empty() : Optional.of(id);
		}
		if (customer != null && customer.isObject() && customer.has("id") && customer.get("id").isTextual()) {
			String id = customer.get("id").asText();
			return id.isBlank() ? Optional.empty() : Optional.of(id);
		}
		JsonNode objType = dataObject.get("object");
		if (objType != null && objType.isTextual() && "customer".equals(objType.asText()) && dataObject.has("id")
				&& dataObject.get("id").isTextual()) {
			String id = dataObject.get("id").asText();
			return id.isBlank() ? Optional.empty() : Optional.of(id);
		}
		return Optional.empty();
	}
}
