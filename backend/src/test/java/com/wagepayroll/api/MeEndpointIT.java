package com.wagepayroll.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
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

/**
 * {@code GET /api/v1/me} with host-based tenant context. Seeded data from {@code DataScaffoldSeed1}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeEndpointIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void anonymousReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "demo.lvh.me")).andExpect(status().isUnauthorized());
	}

	@Test
	void returns401WhenPrincipalUserIdNotInDatabase() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "demo.lvh.me").with(user("99999999-9999-9999-9999-999999999999")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsMeWithoutTenantWhenHostHasNoTenant() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "localhost:8300").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("admin@demo.lvh.me"))
				.andExpect(jsonPath("$.data.userId").value(ADMIN_USER_ID))
				.andExpect(jsonPath("$.data.locale").value("en"))
				.andExpect(jsonPath("$.data.tenantHandle").value(nullValue()))
				.andExpect(jsonPath("$.data.tenantId").value(nullValue()))
				.andExpect(jsonPath("$.data.privileges").isEmpty())
				.andExpect(jsonPath("$.data.planFeatureCodes").isEmpty())
				.andExpect(jsonPath("$.data.applicationName").doesNotExist())
				.andExpect(jsonPath("$.data.dateFormat").doesNotExist())
				.andExpect(jsonPath("$.data.publicBaseUrl").doesNotExist());
	}

	@Test
	void returnsMeWithPrivilegesOnDemoTenantHost() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("admin@demo.lvh.me"))
				.andExpect(jsonPath("$.data.userId").value(ADMIN_USER_ID))
				.andExpect(jsonPath("$.data.locale").value("en"))
				.andExpect(jsonPath("$.data.tenantHandle").value("demo"))
				.andExpect(jsonPath("$.data.tenantId").value("10000000-0000-0000-0000-000000000001"))
				.andExpect(jsonPath("$.data.privileges", hasItem("USER_VIEW")))
				.andExpect(jsonPath("$.data.planFeatureCodes").isEmpty())
				.andExpect(jsonPath("$.data.applicationName").value("Wage Payroll"))
				.andExpect(jsonPath("$.data.dateFormat").value("yyyy-MM-dd"))
				.andExpect(jsonPath("$.data.publicBaseUrl").value("http://auth.lvh.me:3007"));
	}

	@Test
	void returns404ForUnknownTenantHandle() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "nosuchtenant.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isNotFound());
	}
}
