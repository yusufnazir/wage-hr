package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Privilege-gated endpoint: USER_VIEW. Seeded users from {@code DataScaffoldSeed1}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoPrivilegedEndpointIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returns403WhenTenantContextMissing() throws Exception {
		mockMvc.perform(get("/api/v1/demo/user-view").header("Host", "localhost:8300").with(user(ADMIN_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void returns403WhenUserLacksPrivilege() throws Exception {
		mockMvc.perform(
				get("/api/v1/demo/user-view").header("Host", "demo.lvh.me").with(user(NOCODE_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void returns200WhenUserHasPrivilegeInTenant() throws Exception {
		mockMvc.perform(get("/api/v1/demo/user-view").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk());
	}
}
