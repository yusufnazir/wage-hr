package com.wagepayroll.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/**
 * Tenant user directory and edit APIs ({@code /api/v1/tenant/users}). Seeded data from {@code DataScaffoldSeed1}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantUsersIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";

	private static final String ROLE_VIEWER_ID = "40000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listReturnsMembersForViewer() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/users").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(3)))
				.andExpect(jsonPath("$.data.totalElements").value(3));
	}

	@Test
	void viewerCannotLoadAnotherUserDetail() throws Exception {
		mockMvc.perform(
				get("/api/v1/tenant/users/" + ADMIN_USER_ID).header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void viewerMayLoadOwnDetail() throws Exception {
		mockMvc.perform(
				get("/api/v1/tenant/users/" + VIEWER_USER_ID).header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.email").value("viewer@demo.lvh.me"))
				.andExpect(jsonPath("$.data.user.assignableRoles").isEmpty());
	}

	@Test
	void viewerCannotPatch() throws Exception {
		String body = "{\"email\":\"viewer2@demo.lvh.me\"}";
		mockMvc.perform(patch("/api/v1/tenant/users/" + VIEWER_USER_ID).header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminPatchesNocodeEmailAndAssignsViewerRole() throws Exception {
		String body = "{\"email\":\"nocode-renamed@demo.lvh.me\",\"roleIds\":[\"" + ROLE_VIEWER_ID + "\"]}";
		mockMvc.perform(patch("/api/v1/tenant/users/" + NOCODE_USER_ID).header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isNoContent());
		mockMvc.perform(
				get("/api/v1/tenant/users/" + NOCODE_USER_ID).header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.email").value("nocode-renamed@demo.lvh.me"))
				.andExpect(jsonPath("$.data.user.roleNames[0]").value("Viewer"));
	}

	@Test
	void adminCannotChangeOwnRolesViaPatch() throws Exception {
		String body = "{\"roleIds\":[\"" + ROLE_VIEWER_ID + "\"]}";
		mockMvc.perform(patch("/api/v1/tenant/users/" + ADMIN_USER_ID).header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listFiltersByRoleName() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/users").param("role", "Viewer").header("Host", "demo.lvh.me")
				.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].userId").value(VIEWER_USER_ID));
	}

	@Test
	void getUnknownUserInTenantReturns404() throws Exception {
		String random = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
		mockMvc.perform(get("/api/v1/tenant/users/" + random).header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isNotFound());
	}

	@Test
	void roleOptionsReturnsTenantRoles() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/users/role-options").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.roles").isArray())
				.andExpect(jsonPath("$.data.roles.length()").value(2));
	}
}
