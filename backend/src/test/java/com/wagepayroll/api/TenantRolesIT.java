package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class TenantRolesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	// Seeded scaffold role ids (see Liquibase tasks)
	private static final String ROLE_ADMIN = "40000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listForbiddenWithoutRoleView() throws Exception {
		// viewer on acme has narrow pool; should not have ROLE_VIEW.
		mockMvc.perform(get("/api/v1/tenant/roles").header("Host", "acme.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void listOkWithRoleView() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/roles").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());
	}

	@Test
	void createForbiddenWithoutRoleEdit() throws Exception {
		mockMvc.perform(post("/api/v1/tenant/roles").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ops\",\"privilegeCodes\":[]}").with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void createOkWithRoleEdit() throws Exception {
		mockMvc.perform(post("/api/v1/tenant/roles").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ops\",\"privilegeCodes\":[\"ROLE_VIEW\"]}").with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.role.id").isNotEmpty())
				.andExpect(jsonPath("$.data.role.name").value("Ops"))
				.andExpect(jsonPath("$.data.role.privilegeCodes[0]").value("ROLE_VIEW"));
	}

	@Test
	void selfLockoutPreventionRejectsRemovingLastRoleEdit() throws Exception {
		mockMvc.perform(patch("/api/v1/tenant/roles/" + ROLE_ADMIN).header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content("{\"privilegeCodes\":[\"ROLE_VIEW\"]}")
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CANNOT_LOCK_OUT_SELF"));
	}
}

