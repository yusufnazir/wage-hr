package com.wagepayroll.billing;

import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.wagepayroll.domain.billing.BillingProviderLinkEntity;
import com.wagepayroll.domain.billing.BillingProviderLinkRepository;

import org.springframework.stereotype.Service;

/**
 * Versioned PayPal webhook → tenant resolution using {@code billing_provider_link} for provider {@code PAYPAL}.
 *
 * <p><strong>Extraction order</strong> (first non-blank textual value wins; only opaque ids, never email):</p>
 * <ol>
 * <li>{@code resource.payer.payer_id}</li>
 * <li>{@code resource.payer_id}</li>
 * <li>{@code resource.subscriber.payer_id}</li>
 * </ol>
 */
@Service
public class PayPalTenantResolverV1 {

	public static final String VERSION = "PayPalTenantResolverV1";

	private final BillingProviderLinkRepository billingProviderLinkRepository;

	public PayPalTenantResolverV1(BillingProviderLinkRepository billingProviderLinkRepository) {
		this.billingProviderLinkRepository = billingProviderLinkRepository;
	}

	public TenantResolutionResult resolve(JsonNode root) {
		if (root == null || !root.isObject()) {
			return TenantResolutionResult.insufficientData("invalid_root", null, VERSION);
		}
		JsonNode resource = root.get("resource");
		if (resource == null || resource.isNull()) {
			return TenantResolutionResult.insufficientData("resource_missing", "resource", VERSION);
		}
		if (!resource.isObject()) {
			return TenantResolutionResult.insufficientData("resource_not_object", "resource", VERSION);
		}
		Optional<String> payerId = extractPayerId(resource);
		if (payerId.isEmpty()) {
			return TenantResolutionResult.insufficientData("payer_missing", "resource.payer", VERSION);
		}
		Optional<BillingProviderLinkEntity> link = billingProviderLinkRepository.findByProviderAndExternalCustomerId(
				BillingWebhookProvider.PAYPAL, payerId.get());
		if (link.isEmpty()) {
			return TenantResolutionResult.noMatch("billing_provider_link_not_found", VERSION);
		}
		return TenantResolutionResult.resolved(link.get().getTenantId(), VERSION);
	}

	/**
	 * Ordered extraction per class javadoc.
	 */
	static Optional<String> extractPayerId(JsonNode resource) {
		Optional<String> fromPayer = textAt(resource, "payer", "payer_id");
		if (fromPayer.isPresent()) {
			return fromPayer;
		}
		Optional<String> flat = textIfNonBlank(resource, "payer_id");
		if (flat.isPresent()) {
			return flat;
		}
		return textAt(resource, "subscriber", "payer_id");
	}

	private static Optional<String> textAt(JsonNode parent, String child, String leaf) {
		JsonNode node = parent.get(child);
		if (node == null || !node.isObject()) {
			return Optional.empty();
		}
		return textIfNonBlank(node, leaf);
	}

	private static Optional<String> textIfNonBlank(JsonNode parent, String field) {
		JsonNode n = parent.get(field);
		if (n == null || !n.isTextual()) {
			return Optional.empty();
		}
		String v = n.asText();
		return v.isBlank() ? Optional.empty() : Optional.of(v);
	}
}
