package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformPlanFeaturesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void planFeaturesForbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/plan-features").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void planFeaturesOkForPlatformSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/plan-features").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.features.length()").value(5))
				.andExpect(jsonPath("$.data.features[0].code").value("TENANT_CORE"))
				.andExpect(jsonPath("$.data.features[4].code").value("COMMERCIAL_BILLING"));
	}
}
