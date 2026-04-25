package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.plan.PlanFeatureRepository;
import com.wagepayroll.domain.setting.PlatformSettingEntity;
import com.wagepayroll.domain.setting.PlatformSettingRepository;

import org.junit.jupiter.api.BeforeEach;
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
class TenantBillingStripeCustomerNotLinkedIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlatformSettingRepository platformSettingRepository;

	@Autowired
	private PlanFeatureRepository planFeatureRepository;

	@Autowired
	private ObjectMapper objectMapper;

	private UUID commercialPlanId;

	@BeforeEach
	void enableStripeBillingOnPlatformAndCreatePlan() throws Exception {
		PlatformSettingEntity e = platformSettingRepository.findByKey("billing.stripe.enabled").orElseThrow();
		e.setValueText("1");
		platformSettingRepository.save(e);
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		String code = "m3_" + Long.toHexString(System.nanoTime());
		String createJson = "{\"code\":\"%s\",\"sortOrder\":99,\"active\":true,\"planFeatureIds\":[\"%s\"],\"stripeSubscriptionPriceId\":\"price_test_1\"}"
				.formatted(code, tenantCore);
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		commercialPlanId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText());
	}

	@Test
	void checkoutSessionReturns404WhenNoStripeCustomerLink() throws Exception {
		String body = "{\"commercialPlanId\":\"%s\",\"priceId\":\"price_test_1\",\"successUrl\":\"http://localhost:3007/ok\",\"cancelUrl\":\"http://localhost:3007/cancel\"}"
				.formatted(commercialPlanId);
		mockMvc.perform(post("/api/v1/tenant/billing/stripe/checkout-session").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNotFound());
	}

	@Test
	void billingPortalSessionReturns404WhenNoStripeCustomerLink() throws Exception {
		String body = "{\"returnUrl\":\"http://localhost:3007/return\"}";
		mockMvc.perform(post("/api/v1/tenant/billing/stripe/billing-portal-session").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNotFound());
	}
}
