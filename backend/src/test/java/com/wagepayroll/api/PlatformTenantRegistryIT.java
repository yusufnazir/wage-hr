package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PlatformTenantRegistryIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final UUID DEMO_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listForbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/tenants").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void listOkForSuperadminIncludesSeededTenants() throws Exception {
		mockMvc.perform(get("/api/v1/platform/tenants").param("page", "0").param("size", "20").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.items[0].handle").value("acme"))
				.andExpect(jsonPath("$.data.items[1].handle").value("demo"));
	}

	@Test
	void createThenDuplicateHandle() throws Exception {
		String create = "{\"handle\":\"newco\",\"name\":\"New Co Ltd\"}";
		mockMvc.perform(post("/api/v1/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(create)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.tenant.handle").value("newco")).andExpect(jsonPath("$.data.tenant.name").value("New Co Ltd"));

		mockMvc.perform(post("/api/v1/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(create)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isConflict());
	}

	@Test
	void createRejectedForReservedHandle() throws Exception {
		String body = "{\"handle\":\"auth\",\"name\":\"Bad\"}";
		mockMvc.perform(post("/api/v1/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void getOneAndPatchName() throws Exception {
		mockMvc.perform(get("/api/v1/platform/tenants/" + DEMO_TENANT_ID).with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.tenant.handle").value("demo"));

		String patch = "{\"name\":\"Demo Tenant Renamed\"}";
		mockMvc.perform(patch("/api/v1/platform/tenants/" + DEMO_TENANT_ID).contentType(MediaType.APPLICATION_JSON).content(patch)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.tenant.name").value("Demo Tenant Renamed"));
	}

	@Test
	void getOneNotFound() throws Exception {
		UUID random = UUID.fromString("20000000-0000-0000-0000-000000000099");
		mockMvc.perform(get("/api/v1/platform/tenants/" + random).with(user(ADMIN_USER_ID))).andExpect(status().isNotFound());
	}
}
