package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.stripe.net.Webhook;
import com.wagepayroll.billing.StripeTenantResolverV1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BillingWebhooksIT {

	private static final String WEBHOOK_SECRET = "whsec_test_secret";
	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String DEMO_TENANT_ID = "10000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void stripeWebhookAcceptsValidSignatureAndIdempotentReplay() throws Exception {
		String payload = "{\"id\":\"evt_m3_billing_webhooks_it_1\",\"object\":\"event\"}";
		String sig = signStripeWebhook(payload, WEBHOOK_SECRET);
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload)
				.header("Stripe-Signature", sig)).andExpect(status().isOk()).andExpect(jsonPath("$.received").value(true))
				.andExpect(jsonPath("$.duplicate").value(false))
				.andExpect(jsonPath("$.tenantResolutionState").value("UNRESOLVED_INSUFFICIENT_DATA"))
				.andExpect(jsonPath("$.tenantResolutionReasonCode").value("stripe_customer_missing"))
				.andExpect(jsonPath("$.tenantResolutionResolverVersion").value(StripeTenantResolverV1.VERSION));

		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload)
				.header("Stripe-Signature", sig)).andExpect(status().isOk()).andExpect(jsonPath("$.received").value(true))
				.andExpect(jsonPath("$.duplicate").value(true))
				.andExpect(jsonPath("$.tenantResolutionState").value("UNRESOLVED_INSUFFICIENT_DATA"));
	}

	@Test
	void stripeWebhookTenantResolutionResolvedWhenCustomerLinked() throws Exception {
		String customerId = "cus_map_demo_webhook";
		String putBody = "{\"externalCustomerId\":\"" + customerId + "\"}";
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/billing-provider-links/stripe")
				.contentType(MediaType.APPLICATION_JSON).content(putBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk());

		String payload = "{\"id\":\"evt_map_demo_webhook_1\",\"object\":\"event\",\"data\":{\"object\":{\"id\":\"sub_x\",\"object\":\"subscription\",\"customer\":\"%s\"}}}"
				.formatted(customerId);
		String sig = signStripeWebhook(payload, WEBHOOK_SECRET);
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload)
				.header("Stripe-Signature", sig)).andExpect(status().isOk()).andExpect(jsonPath("$.tenantResolutionState").value("RESOLVED"))
				.andExpect(jsonPath("$.duplicate").value(false))
				.andExpect(jsonPath("$.tenantResolutionResolverVersion").value(StripeTenantResolverV1.VERSION));
	}

	@Test
	void stripeWebhookTenantResolutionNoMatchWhenCustomerUnknown() throws Exception {
		String payload = "{\"id\":\"evt_stripe_no_match_1\",\"object\":\"event\",\"data\":{\"object\":{\"id\":\"sub_x\",\"object\":\"subscription\",\"customer\":\"cus_orphan_stripe_it\"}}}";
		String sig = signStripeWebhook(payload, WEBHOOK_SECRET);
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload)
				.header("Stripe-Signature", sig)).andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantResolutionState").value("UNRESOLVED_NO_MATCH"))
				.andExpect(jsonPath("$.tenantResolutionReasonCode").value("billing_provider_link_not_found"));
	}

	@Test
	void stripeWebhookRejectsBadSignature() throws Exception {
		String payload = "{\"id\":\"evt_m3_billing_webhooks_it_2\",\"object\":\"event\"}";
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload)
				.header("Stripe-Signature", "t=" + Instant.now().getEpochSecond() + ",v1=deadbeef")).andExpect(status().isBadRequest());
	}

	private static String signStripeWebhook(String payload, String secret) throws Exception {
		long timestamp = Instant.now().getEpochSecond();
		String signedPayload = timestamp + "." + payload;
		String v1 = Webhook.Util.computeHmacSha256(secret, signedPayload);
		return "t=" + timestamp + ",v1=" + v1;
	}
}
