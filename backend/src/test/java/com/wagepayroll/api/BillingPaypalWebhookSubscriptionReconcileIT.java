package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.billing.PaypalSubscriptionCustomId;
import com.wagepayroll.domain.plan.PlanFeatureRepository;
import com.wagepayroll.subscription.TenantSubscriptionStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class BillingPaypalWebhookSubscriptionReconcileIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String DEMO_TENANT_ID = "10000000-0000-0000-0000-000000000001";
	private static final String PAYER_ID = "PAYER_PP_RECONCILE_IT";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PlanFeatureRepository planFeatureRepository;

	@Test
	void subscriptionActivatedUpsertsTenantSubscriptionIdempotently() throws Exception {
		UUID planId = createCommercialPlanWithoutStripePrice();
		linkPaypalPayer();

		String customId = PaypalSubscriptionCustomId.encode(UUID.fromString(DEMO_TENANT_ID), planId);
		String body = subscriptionActivatedJson(customId, PAYER_ID);
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_sub_act_1")).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.subscription.status").value(TenantSubscriptionStatus.ACTIVE.code()))
				.andExpect(jsonPath("$.data.subscription.commercialPlanId").value(planId.toString()));

		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_sub_act_1")).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.subscription.status").value(TenantSubscriptionStatus.ACTIVE.code()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "BILLING.SUBSCRIPTION.CANCELLED", "BILLING.SUBSCRIPTION.EXPIRED", "BILLING.SUBSCRIPTION.SUSPENDED" })
	void subscriptionNotEntitledEventsMarkCancelled(String eventType) throws Exception {
		UUID planId = createCommercialPlanWithoutStripePrice();
		linkPaypalPayer();

		String customId = PaypalSubscriptionCustomId.encode(UUID.fromString(DEMO_TENANT_ID), planId);
		String activated = subscriptionActivatedJson(customId, PAYER_ID);
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(activated)
				.header("PayPal-Transmission-Id", "tr_pp_sub_act_" + eventType)).andExpect(status().isOk());

		String trTerm = "tr_pp_sub_term_" + eventType.replace('.', '_');
		String terminated = subscriptionTerminatedJson(eventType, PAYER_ID);
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(terminated)
				.header("PayPal-Transmission-Id", trTerm)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.subscription.status").value(TenantSubscriptionStatus.CANCELLED.code()))
				.andExpect(jsonPath("$.data.subscription.commercialPlanId").value(planId.toString()));
	}

	@Test
	void subscriptionReActivatedUpsertsActiveLikeActivated() throws Exception {
		UUID planId = createCommercialPlanWithoutStripePrice();
		linkPaypalPayer();

		String customId = PaypalSubscriptionCustomId.encode(UUID.fromString(DEMO_TENANT_ID), planId);
		String activated = subscriptionActivatedJson(customId, PAYER_ID);
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(activated)
				.header("PayPal-Transmission-Id", "tr_pp_sub_act_re1")).andExpect(status().isOk());

		String reactivated = subscriptionLifecycleJson("BILLING.SUBSCRIPTION.RE-ACTIVATED", customId, PAYER_ID);
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(reactivated)
				.header("PayPal-Transmission-Id", "tr_pp_sub_react_1")).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.subscription.status").value(TenantSubscriptionStatus.ACTIVE.code()))
				.andExpect(jsonPath("$.data.subscription.commercialPlanId").value(planId.toString()));
	}

	private UUID createCommercialPlanWithoutStripePrice() throws Exception {
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		String code = "pp_wh_" + Long.toHexString(System.nanoTime());
		String createJson = "{\"code\":\"%s\",\"sortOrder\":7,\"active\":true,\"planFeatureIds\":[\"%s\"]}".formatted(code, tenantCore);
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		return UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText());
	}

	private void linkPaypalPayer() throws Exception {
		String putBody = "{\"externalCustomerId\":\"" + PAYER_ID + "\"}";
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/billing-provider-links/paypal")
				.contentType(MediaType.APPLICATION_JSON).content(putBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk());
	}

	private static String subscriptionActivatedJson(String customId, String payerId) {
		return subscriptionLifecycleJson("BILLING.SUBSCRIPTION.ACTIVATED", customId, payerId);
	}

	private static String subscriptionTerminatedJson(String eventType, String payerId) {
		return """
				{"id":"WH-PP-TERM","event_type":"%s","webhook_id":"test_paypal_webhook_id_1","resource":{"id":"I-SUB-TERM","subscriber":{"payer_id":"%s"}}}
				""".formatted(eventType, payerId);
	}

	private static String subscriptionLifecycleJson(String eventType, String customId, String payerId) {
		return """
				{"id":"WH-PP-LIFE","event_type":"%s","webhook_id":"test_paypal_webhook_id_1","resource":{"id":"I-SUB-1","custom_id":"%s","subscriber":{"payer_id":"%s"}}}
				""".formatted(eventType, customId, payerId);
	}
}
