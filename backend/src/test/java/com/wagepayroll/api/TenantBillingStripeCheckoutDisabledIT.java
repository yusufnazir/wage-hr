package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class TenantBillingStripeCheckoutDisabledIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void checkoutSessionReturns403WhenStripeBillingDisabledOnPlatform() throws Exception {
		String body = "{\"commercialPlanId\":\"10000000-0000-0000-0000-000000000099\",\"priceId\":\"price_test_1\",\"successUrl\":\"http://localhost:3007/ok\",\"cancelUrl\":\"http://localhost:3007/cancel\"}";
		mockMvc.perform(post("/api/v1/tenant/billing/stripe/checkout-session").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isForbidden());
	}

	@Test
	void billingPortalSessionReturns403WhenStripeBillingDisabledOnPlatform() throws Exception {
		String body = "{\"returnUrl\":\"http://localhost:3007/return\"}";
		mockMvc.perform(post("/api/v1/tenant/billing/stripe/billing-portal-session").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isForbidden());
	}
}
