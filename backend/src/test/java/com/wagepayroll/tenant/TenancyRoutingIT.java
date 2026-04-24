package com.wagepayroll.tenant;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenancyRoutingIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unknownTenantSubdomainReturns404ProblemForApi() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "nosuchtenant.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("Unknown tenant"));
	}

	@Test
	void apiHostWithInvalidXTenantIdReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "api.lvh.me").header("X-Tenant-Id", "not-a-uuid")
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("Invalid X-Tenant-Id header"));
	}

	@Test
	void apiHostWithUnknownXTenantIdReturns404() throws Exception {
		mockMvc.perform(
				get("/api/v1/me").header("Host", "api.lvh.me")
						.header("X-Tenant-Id", "99999999-9999-9999-9999-999999999999")
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("Unknown tenant id"));
	}

	@Test
	void apiHostWithoutXTenantIdLeavesTenantUnset() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "api.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.locale").value("en"))
				.andExpect(jsonPath("$.data.tenantHandle").value(nullValue()))
				.andExpect(jsonPath("$.data.privileges").isArray());
	}
}
