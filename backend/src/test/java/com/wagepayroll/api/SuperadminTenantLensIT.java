package com.wagepayroll.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.wagepayroll.security.BreakGlassHeaders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform superadmin tenant lens: {@code admin.{base}} host + {@code X-Tenant-Id} without membership under
 * global-catalog elevation behavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SuperadminTenantLensIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";

	private static final UUID ACME_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
	private static final UUID ADMIN_UUID = UUID.fromString("30000000-0000-0000-0000-000000000001");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void removeAdminAcmeMembership() {
		jdbcTemplate.update("DELETE FROM user_role WHERE tenant_id = ? AND user_id = ?", ACME_TENANT_ID, ADMIN_UUID);
		jdbcTemplate.update("DELETE FROM membership WHERE tenant_id = ? AND user_id = ?", ACME_TENANT_ID, ADMIN_UUID);
	}

	@Test
	void superadminMayCallTenantRouteViaAppHostAndXTenantIdWithoutMembership() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/users").header("Host", "admin.lvh.me").header("X-Tenant-Id", ACME_TENANT_ID.toString())
				.with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	void xTenantIdOverridesHostTenantForSuperadminLensOnTenantSubdomain() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "demo.lvh.me").header("X-Tenant-Id", ACME_TENANT_ID.toString())
				.with(user(ADMIN_USER_ID))).andExpect(status().isOk()).andExpect(jsonPath("$.data.tenantHandle").value("acme"))
				.andExpect(jsonPath("$.data.tenantId").value(ACME_TENANT_ID.toString()));
	}

	@Test
	void meReturnsGlobalCatalogPrivilegesForSuperadminLensWithoutMembership() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Host", "admin.lvh.me").header("X-Tenant-Id", ACME_TENANT_ID.toString())
				.with(user(ADMIN_USER_ID))).andExpect(status().isOk()).andExpect(jsonPath("$.data.tenantHandle").value("acme"))
				.andExpect(jsonPath("$.data.tenantId").value(ACME_TENANT_ID.toString()))
				.andExpect(jsonPath("$.data.privileges", hasItem("USER_VIEW")))
				.andExpect(jsonPath("$.data.privileges.length()").value(14));
	}

	@Test
	void nonSuperadminForbiddenForTenantRouteWithForeignXTenantId() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/users").header("Host", "admin.lvh.me").header("X-Tenant-Id", ACME_TENANT_ID.toString())
				.with(user(NOCODE_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void superadminCanPatchTenantSettingsWithBreakGlassWithoutMembership() throws Exception {
		String body = "{\"entries\":[{\"key\":\"tenant.acme_superadmin_probe\",\"value\":\"1\"}]}";
		mockMvc.perform(
				patch("/api/v1/tenant/settings").header("Host", "admin.lvh.me").header("X-Tenant-Id", ACME_TENANT_ID.toString())
						.contentType(MediaType.APPLICATION_JSON).content(body)
						.header(BreakGlassHeaders.REASON, "Support ticket ACME-99 — attempt patch without pool privilege")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isNoContent());
	}
}
