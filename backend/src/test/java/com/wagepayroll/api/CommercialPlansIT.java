package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.plan.PlanFeatureRepository;

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
class CommercialPlansIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String DEMO_TENANT_ID = "10000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PlanFeatureRepository planFeatureRepository;

	@Test
	void commercialPlansForbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/commercial-plans").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void createListGetAndReplacePlan() throws Exception {
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		UUID hr = planFeatureRepository.findByCode("HR_ESSENTIALS").orElseThrow().getId();
		UUID payroll = planFeatureRepository.findByCode("PAYROLL_COUNTRY").orElseThrow().getId();

		String createJson = "{\"code\":\"m3_basis\",\"sortOrder\":5,\"active\":true,\"planFeatureIds\":[\"%s\",\"%s\"]}"
				.formatted(tenantCore.toString(), hr.toString());
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.code").value("M3_BASIS"))
				.andExpect(jsonPath("$.data.planFeatureCodes.length()").value(2)).andReturn();
		JsonNode root = objectMapper.readTree(created.getResponse().getContentAsString());
		String planId = root.get("data").get("id").asText();

		mockMvc.perform(get("/api/v1/platform/commercial-plans").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.plans.length()").value(2)).andExpect(jsonPath("$.data.plans[1].code").value("M3_BASIS"))
				.andExpect(jsonPath("$.data.plans[1].featureCount").value(2));

		mockMvc.perform(get("/api/v1/platform/commercial-plans/" + planId).with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.planFeatureCodes[0]").value("TENANT_CORE"))
				.andExpect(jsonPath("$.data.planFeatureCodes[1]").value("HR_ESSENTIALS"));

		String putJson = "{\"sortOrder\":1,\"active\":true,\"planFeatureIds\":[\"%s\",\"%s\",\"%s\"],\"stripeSubscriptionPriceId\":null,\"clearStripeSubscriptionPrice\":false}"
				.formatted(tenantCore.toString(), hr.toString(), payroll.toString());
		mockMvc.perform(put("/api/v1/platform/commercial-plans/" + planId).contentType(MediaType.APPLICATION_JSON).content(putJson)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.planFeatureCodes.length()").value(3))
				.andExpect(jsonPath("$.data.planFeatureCodes[2]").value("PAYROLL_COUNTRY"));
	}

	@Test
	void deleteUnusedCommercialPlan() throws Exception {
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		String createJson = "{\"code\":\"m3_delme\",\"sortOrder\":2,\"active\":true,\"planFeatureIds\":[\"%s\"]}"
				.formatted(tenantCore.toString());
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String planId = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();

		mockMvc.perform(delete("/api/v1/platform/commercial-plans/" + planId).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/platform/commercial-plans/" + planId).with(user(ADMIN_USER_ID))).andExpect(status().isNotFound());
	}

	@Test
	void deleteCommercialPlanConflictWhenAssignedToSubscription() throws Exception {
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		String createJson = "{\"code\":\"m3_inuse\",\"sortOrder\":2,\"active\":true,\"planFeatureIds\":[\"%s\"]}"
				.formatted(tenantCore.toString());
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String planId = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();

		String subBody = "{\"commercialPlanId\":\"" + planId + "\",\"status\":\"ACTIVE\"}";
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").contentType(MediaType.APPLICATION_JSON)
				.content(subBody).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/platform/commercial-plans/" + planId).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isConflict());
	}

	@Test
	void createPlanRejectsInvalidPaypalBillingPlanId() throws Exception {
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		String createJson = "{\"code\":\"m3_badpp\",\"sortOrder\":2,\"active\":true,\"planFeatureIds\":[\"%s\"],\"paypalBillingPlanId\":\"not_paypal\"}"
				.formatted(tenantCore.toString());
		mockMvc.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createJson)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void createPlanConflictWhenPaypalBillingPlanIdReused() throws Exception {
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		String paypal = "P-DUPpp_" + Long.toHexString(System.nanoTime());
		String createJsonA = "{\"code\":\"m3_ppa\",\"sortOrder\":2,\"active\":true,\"planFeatureIds\":[\"%s\"],\"paypalBillingPlanId\":\"%s\"}"
				.formatted(tenantCore.toString(), paypal);
		mockMvc.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createJsonA)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isCreated());

		String createJsonB = "{\"code\":\"m3_ppb\",\"sortOrder\":3,\"active\":true,\"planFeatureIds\":[\"%s\"],\"paypalBillingPlanId\":\"%s\"}"
				.formatted(tenantCore.toString(), paypal);
		mockMvc.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createJsonB)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isConflict());
	}
}
