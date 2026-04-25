package com.wagepayroll.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.plan.PlanFeatureRepository;

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
class TenantBillingSummaryIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";
	private static final String DEMO_TENANT_ID = "10000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PlanFeatureRepository planFeatureRepository;

	@Test
	void summaryReturnsPlatformFlagsAndLinkPresenceForAdmin() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/billing/summary").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.stripeBillingEnabled").value(false))
				.andExpect(jsonPath("$.data.summary.paypalBillingEnabled").value(false))
				.andExpect(jsonPath("$.data.summary.stripeCustomerLinked").value(false))
				.andExpect(jsonPath("$.data.summary.paypalCustomerLinked").value(false))
				.andExpect(jsonPath("$.data.summary.subscription", nullValue()));
	}

	@Test
	void summaryOkForViewerOnTenantHost() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/billing/summary").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.stripeBillingEnabled").exists());
	}

	@Test
	void summaryForbiddenWhenUserLacksUserView() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/billing/summary").header("Host", "demo.lvh.me").with(user(NOCODE_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void summaryIncludesSubscriptionSnapshotWhenTenantHasSubscription() throws Exception {
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		String createJson = "{\"code\":\"m3_bsum\",\"sortOrder\":2,\"active\":true,\"planFeatureIds\":[\"%s\"]}"
				.formatted(tenantCore.toString());
		String planId = objectMapper
				.readTree(mockMvc
						.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON)
								.content(createJson).with(user(ADMIN_USER_ID)).with(csrf()))
						.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
				.get("data").get("id").asText();

		String subBody = "{\"commercialPlanId\":\"" + planId + "\",\"status\":\"ACTIVE\"}";
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").contentType(MediaType.APPLICATION_JSON)
				.content(subBody).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/tenant/billing/summary").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.subscription.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.summary.subscription.commercialPlanId").value(planId))
				.andExpect(jsonPath("$.data.summary.subscription.commercialPlanCode").value("M3_BSUM"));
	}

	@Test
	void commercialPlansCatalogExcludesInactivePlans() throws Exception {
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		String inactiveJson = "{\"code\":\"m3_cat_off\",\"sortOrder\":91,\"active\":false,\"planFeatureIds\":[\"%s\"]}"
				.formatted(tenantCore.toString());
		mockMvc.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(inactiveJson)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isCreated());

		String activeJson = "{\"code\":\"m3_cat_on\",\"sortOrder\":92,\"active\":true,\"planFeatureIds\":[\"%s\"]}"
				.formatted(tenantCore.toString());
		mockMvc.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(activeJson)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/tenant/billing/commercial-plans").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.plans[*].code", not(hasItem("M3_CAT_OFF"))))
				.andExpect(jsonPath("$.data.plans[*].code", hasItem("M3_CAT_ON")));
	}

	@Test
	void commercialPlansForbiddenForViewer() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/billing/commercial-plans").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isForbidden());
	}
}
