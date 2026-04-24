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
class NavigationAndSettingsIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void navigationReturns400WhenTenantContextMissing() throws Exception {
		mockMvc.perform(get("/api/v1/me/navigation").header("Host", "localhost:8300").with(user(ADMIN_USER_ID)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void navigationReturnsFilteredItemsForViewer() throws Exception {
		mockMvc.perform(get("/api/v1/me/navigation").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2));
	}

	@Test
	void navigationReturnsAllSeededRootsForAdmin() throws Exception {
		mockMvc.perform(get("/api/v1/me/navigation").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(3));
	}

	@Test
	void platformSettingsForbiddenForNonOperator() throws Exception {
		mockMvc.perform(get("/api/v1/platform/settings").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void platformSettingsOkForSeededPlatformSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/settings").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[0].key").value("platform.product_name"));
	}
}
