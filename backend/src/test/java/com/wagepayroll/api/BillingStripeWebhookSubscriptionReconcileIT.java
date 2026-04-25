package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.net.Webhook;
import com.wagepayroll.domain.plan.PlanFeatureRepository;
import com.wagepayroll.subscription.TenantSubscriptionStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BillingStripeWebhookSubscriptionReconcileIT {

	private static final String WEBHOOK_SECRET = "whsec_test_secret";
	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String DEMO_TENANT_ID = "10000000-0000-0000-0000-000000000001";
	private static final String CUSTOMER_ID = "cus_map_demo_webhook";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PlanFeatureRepository planFeatureRepository;

	@Test
	void checkoutSessionCompletedActivatesTenantSubscriptionIdempotently() throws Exception {
		UUID planId = createCommercialPlanWithStripePrice("price_reconcile_checkout_it");
		linkStripeCustomer();

		String eventId = "evt_checkout_reconcile_1";
		String payload = checkoutSessionCompletedPayload(eventId, planId);
		String sig = signStripeWebhook(payload, WEBHOOK_SECRET);
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload)
				.header("Stripe-Signature", sig)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.subscription.status").value(TenantSubscriptionStatus.ACTIVE.code()))
				.andExpect(jsonPath("$.data.subscription.commercialPlanId").value(planId.toString()));

		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload)
				.header("Stripe-Signature", sig)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.subscription.status").value(TenantSubscriptionStatus.ACTIVE.code()));
	}

	@Test
	void customerSubscriptionUpdatedActiveReconcilesFromStripePriceWhenMetadataMissing() throws Exception {
		UUID planId = createCommercialPlanWithStripePrice("price_reconcile_sub_updated_no_meta_it");
		linkStripeCustomer();

		String evt = "evt_sub_updated_no_meta_1";
		String payload = subscriptionUpdatedPayloadNoMetadata(evt, "active", CUSTOMER_ID, "price_reconcile_sub_updated_no_meta_it");
		String sig = signStripeWebhook(payload, WEBHOOK_SECRET);
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload)
				.header("Stripe-Signature", sig)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.subscription.status").value(TenantSubscriptionStatus.ACTIVE.code()))
				.andExpect(jsonPath("$.data.subscription.commercialPlanId").value(planId.toString()));
	}

	@Test
	void customerSubscriptionUpdatedActiveReconcilesFromSubscriptionMetadata() throws Exception {
		UUID planId = createCommercialPlanWithStripePrice("price_reconcile_sub_updated_it");
		linkStripeCustomer();

		String evt = "evt_sub_updated_reconcile_1";
		String payload = subscriptionUpdatedPayload(evt, "active", CUSTOMER_ID, planId, "price_reconcile_sub_updated_it");
		String sig = signStripeWebhook(payload, WEBHOOK_SECRET);
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload)
				.header("Stripe-Signature", sig)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.subscription.status").value(TenantSubscriptionStatus.ACTIVE.code()))
				.andExpect(jsonPath("$.data.subscription.commercialPlanId").value(planId.toString()));
	}

	@Test
	void customerSubscriptionUpdatedCanceledMarksCancelled() throws Exception {
		UUID planId = createCommercialPlanWithStripePrice("price_reconcile_sub_upd_can_it");
		linkStripeCustomer();

		String checkoutEvt = "evt_checkout_reconcile_upd_can_1";
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON)
				.content(checkoutSessionCompletedPayload(checkoutEvt, planId)).header("Stripe-Signature",
						signStripeWebhook(checkoutSessionCompletedPayload(checkoutEvt, planId), WEBHOOK_SECRET)))
				.andExpect(status().isOk());

		String updEvt = "evt_sub_updated_canceled_1";
		String updPayload = subscriptionUpdatedPayload(updEvt, "canceled", CUSTOMER_ID, planId, "price_reconcile_sub_upd_can_it");
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(updPayload)
				.header("Stripe-Signature", signStripeWebhook(updPayload, WEBHOOK_SECRET))).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.subscription.status").value(TenantSubscriptionStatus.CANCELLED.code()))
				.andExpect(jsonPath("$.data.subscription.commercialPlanId").value(planId.toString()));
	}

	@Test
	void customerSubscriptionDeletedMarksCancelled() throws Exception {
		UUID planId = createCommercialPlanWithStripePrice("price_reconcile_delete_it");
		linkStripeCustomer();

		String checkoutEvt = "evt_checkout_reconcile_del_1";
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON)
				.content(checkoutSessionCompletedPayload(checkoutEvt, planId)).header("Stripe-Signature",
						signStripeWebhook(checkoutSessionCompletedPayload(checkoutEvt, planId), WEBHOOK_SECRET)))
				.andExpect(status().isOk());

		String delEvt = "evt_sub_deleted_reconcile_1";
		String delPayload = """
				{"id":"%s","object":"event","type":"customer.subscription.deleted","data":{"object":{"id":"sub_del_1","object":"subscription","customer":"%s"}}}
				""".formatted(delEvt, CUSTOMER_ID);
		String delSig = signStripeWebhook(delPayload, WEBHOOK_SECRET);
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(delPayload)
				.header("Stripe-Signature", delSig)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.subscription.status").value(TenantSubscriptionStatus.CANCELLED.code()))
				.andExpect(jsonPath("$.data.subscription.commercialPlanId").value(planId.toString()));
	}

	private UUID createCommercialPlanWithStripePrice(String stripePriceId) throws Exception {
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		String code = "m3_" + Long.toHexString(System.nanoTime());
		String createJson = "{\"code\":\"%s\",\"sortOrder\":7,\"active\":true,\"planFeatureIds\":[\"%s\"],\"stripeSubscriptionPriceId\":\"%s\"}"
				.formatted(code, tenantCore, stripePriceId);
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		return UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText());
	}

	private void linkStripeCustomer() throws Exception {
		String putBody = "{\"externalCustomerId\":\"" + CUSTOMER_ID + "\"}";
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/billing-provider-links/stripe")
				.contentType(MediaType.APPLICATION_JSON).content(putBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk());
	}

	private static String checkoutSessionCompletedPayload(String eventId, UUID commercialPlanId) {
		return """
				{"id":"%s","object":"event","type":"checkout.session.completed","data":{"object":{"id":"cs_test_1","object":"checkout.session","mode":"subscription","client_reference_id":"%s","metadata":{"commercial_plan_id":"%s"},"customer":"%s"}}}
				""".formatted(eventId, DEMO_TENANT_ID, commercialPlanId, CUSTOMER_ID);
	}

	private static String subscriptionUpdatedPayload(String eventId, String status, String customerId, UUID commercialPlanId,
			String priceId) {
		return """
				{"id":"%s","object":"event","type":"customer.subscription.updated","data":{"object":{"id":"sub_upd_1","object":"subscription","status":"%s","customer":"%s","metadata":{"commercial_plan_id":"%s"},"items":{"object":"list","data":[{"id":"si_test_1","object":"subscription_item","price":{"id":"%s","object":"price"}}]}}}}
				""".formatted(eventId, status, customerId, commercialPlanId, priceId);
	}

	private static String subscriptionUpdatedPayloadNoMetadata(String eventId, String status, String customerId, String priceId) {
		return """
				{"id":"%s","object":"event","type":"customer.subscription.updated","data":{"object":{"id":"sub_no_meta","object":"subscription","status":"%s","customer":"%s","metadata":{},"items":{"object":"list","data":[{"id":"si_nm","object":"subscription_item","price":{"id":"%s","object":"price"}}]}}}}
				""".formatted(eventId, status, customerId, priceId);
	}

	private static String signStripeWebhook(String payload, String secret) throws Exception {
		long timestamp = Instant.now().getEpochSecond();
		String signedPayload = timestamp + "." + payload;
		String v1 = Webhook.Util.computeHmacSha256(secret, signedPayload);
		return "t=" + timestamp + ",v1=" + v1;
	}
}
