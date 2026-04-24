package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
class PlatformTenantPrivilegePoolIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final UUID ACME_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void replacePoolForbiddenForNonSuperadmin() throws Exception {
		String body = "{\"codes\":[\"USER_VIEW\"]}";
		mockMvc.perform(
				put("/api/v1/platform/tenants/" + ACME_TENANT_ID + "/privilege-pool").contentType(MediaType.APPLICATION_JSON)
						.content(body).with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void superadminReplacesAcmePoolThenTenantPoolReflects() throws Exception {
		String body = "{\"codes\":[\"USER_VIEW\",\"USER_EDIT\",\"TENANT_SETTINGS_EDIT\"]}";
		mockMvc.perform(
				put("/api/v1/platform/tenants/" + ACME_TENANT_ID + "/privilege-pool").contentType(MediaType.APPLICATION_JSON)
						.content(body).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.privileges.length()").value(3));
		mockMvc.perform(get("/api/v1/tenant/privileges/pool").header("Host", "acme.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.privileges.length()").value(3));
	}
}
