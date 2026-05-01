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
class MeTenantsIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listsBothTenantsForAdminSortedByHandle() throws Exception {
		mockMvc.perform(get("/api/v1/me/tenants").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.tenants.length()").value(2))
				.andExpect(jsonPath("$.data.tenants[0].handle").value("acme"))
				.andExpect(jsonPath("$.data.tenants[0].roles[0]").value("Reader"))
				.andExpect(jsonPath("$.data.tenants[1].handle").value("demo"))
				.andExpect(jsonPath("$.data.tenants[1].roles[0]").value("Admin"));
	}

	@Test
	void listsOnlyDemoForNocodeUser() throws Exception {
		mockMvc.perform(get("/api/v1/me/tenants").with(user(NOCODE_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.tenants.length()").value(1))
				.andExpect(jsonPath("$.data.tenants[0].handle").value("demo"));
	}

	@Test
	void navigationOnAcmeIsNarrowerThanDemo() throws Exception {
		mockMvc.perform(get("/api/v1/me/navigation").header("Host", "acme.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(6));
	}
}
