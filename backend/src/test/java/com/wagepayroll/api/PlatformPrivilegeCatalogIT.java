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
				.andExpect(jsonPath("$.data.entries.length()").value(4))
				.andExpect(jsonPath("$.data.entries[0].code").value("TENANT_SETTINGS_EDIT"))
				.andExpect(jsonPath("$.data.entries[0].action").value("EDIT"))
				.andExpect(jsonPath("$.data.entries[0].resource").value("TENANT_SETTINGS"))
				.andExpect(jsonPath("$.data.entries[1].code").value("USER_EDIT"))
				.andExpect(jsonPath("$.data.entries[2].code").value("USER_INVITE"))
				.andExpect(jsonPath("$.data.entries[2].action").value("CREATE"))
				.andExpect(jsonPath("$.data.entries[2].resource").value("TENANT_INVITATION"))
				.andExpect(jsonPath("$.data.entries[3].code").value("USER_VIEW"));
	}
}
