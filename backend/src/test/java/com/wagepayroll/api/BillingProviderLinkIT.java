package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class BillingProviderLinkIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String DEMO_TENANT_ID = "10000000-0000-0000-0000-000000000001";
	private static final String ACME_TENANT_ID = "10000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void forbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/billing-provider-links").with(user(VIEWER_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void putGetAndConflictAcrossTenants() throws Exception {
		String stripeBody = "{\"externalCustomerId\":\"cus_demo_shared\"}";
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/billing-provider-links/stripe")
				.contentType(MediaType.APPLICATION_JSON).content(stripeBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.link.provider").value("STRIPE"))
				.andExpect(jsonPath("$.data.link.externalCustomerId").value("cus_demo_shared"));

		String paypalBody = "{\"externalCustomerId\":\"PAYPAL_PAYER_DEMO\"}";
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/billing-provider-links/paypal")
				.contentType(MediaType.APPLICATION_JSON).content(paypalBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/billing-provider-links").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.links.length()").value(2));

		mockMvc.perform(put("/api/v1/platform/tenants/" + ACME_TENANT_ID + "/billing-provider-links/stripe")
				.contentType(MediaType.APPLICATION_JSON).content(stripeBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isConflict());
	}

	@Test
	void invalidProviderSegment() throws Exception {
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/billing-provider-links/venmo")
				.contentType(MediaType.APPLICATION_JSON).content("{\"externalCustomerId\":\"x\"}").with(user(ADMIN_USER_ID))
				.with(csrf())).andExpect(status().isBadRequest());
	}
}
