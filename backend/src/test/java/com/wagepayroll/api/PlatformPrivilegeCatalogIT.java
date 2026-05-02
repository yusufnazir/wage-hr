package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wagepayroll.security.DefinedPrivilege;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformPrivilegeCatalogIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void catalogForbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/privileges/catalog").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void catalogReturnsEntriesForPlatformSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/privileges/catalog").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries.length()").value(DefinedPrivilege.values().length))
				.andExpect(jsonPath("$.data.entries[*].code").value(org.hamcrest.Matchers.hasItem("COMPANY_MANAGE")))
				.andExpect(jsonPath("$.data.entries[*].code").value(org.hamcrest.Matchers.hasItem("COMPANY_VIEW")))
				.andExpect(jsonPath("$.data.entries[*].code").value(org.hamcrest.Matchers.hasItem("ROLE_EDIT")))
				.andExpect(jsonPath("$.data.entries[*].code").value(org.hamcrest.Matchers.hasItem("ROLE_VIEW")))
				.andExpect(jsonPath("$.data.entries[*].code").value(org.hamcrest.Matchers.hasItem("TENANT_SETTINGS_EDIT")))
				.andExpect(jsonPath("$.data.entries[*].code").value(org.hamcrest.Matchers.hasItem("USER_EDIT")))
				.andExpect(jsonPath("$.data.entries[*].code").value(org.hamcrest.Matchers.hasItem("USER_INVITE")))
				.andExpect(jsonPath("$.data.entries[*].code").value(org.hamcrest.Matchers.hasItem("USER_VIEW")));
	}
}
