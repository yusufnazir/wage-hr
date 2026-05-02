package com.wagepayroll.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubscriptionGatingIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String DEMO_TENANT_ID = "10000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PlanFeatureRepository planFeatureRepository;

	@Test
	void activeSubscriptionPublishesPlanFeatureCodesWithoutTenantPoolMutation() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.privileges", hasItem("USER_INVITE")))
				.andExpect(jsonPath("$.data.planFeatureCodes").isEmpty());

		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		UUID hr = planFeatureRepository.findByCode("HR_ESSENTIALS").orElseThrow().getId();
		String createPlan = "{\"code\":\"m3_gate\",\"sortOrder\":3,\"active\":true,\"planFeatureIds\":[\"%s\",\"%s\"]}"
				.formatted(tenantCore.toString(), hr.toString());
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createPlan)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String planId = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();

		String subBody = "{\"commercialPlanId\":\"%s\",\"status\":\"ACTIVE\"}".formatted(planId);
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").contentType(MediaType.APPLICATION_JSON)
				.content(subBody).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/me").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.privileges", hasItem("USER_INVITE")))
				.andExpect(jsonPath("$.data.planFeatureCodes[0]").value("TENANT_CORE"))
				.andExpect(jsonPath("$.data.planFeatureCodes[1]").value("HR_ESSENTIALS"));

		mockMvc.perform(get("/api/v1/tenant/privileges/pool").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.privileges", hasItem("USER_INVITE")));
	}
}
