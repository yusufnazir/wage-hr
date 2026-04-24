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
class TenantPrivilegePoolIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void poolForbiddenWithoutTenantSettingsEdit() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/privileges/pool").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void poolListsAllowancesOnDemo() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/privileges/pool").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.privileges.length()").value(4))
				.andExpect(jsonPath("$.data.privileges[0]").value("TENANT_SETTINGS_EDIT"))
				.andExpect(jsonPath("$.data.privileges[1]").value("USER_EDIT"))
				.andExpect(jsonPath("$.data.privileges[2]").value("USER_INVITE"))
				.andExpect(jsonPath("$.data.privileges[3]").value("USER_VIEW"));
	}

	@Test
	void poolListsNarrowAllowancesOnAcme() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/privileges/pool").header("Host", "acme.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.privileges.length()").value(1))
				.andExpect(jsonPath("$.data.privileges[0]").value("USER_VIEW"));
	}
}
