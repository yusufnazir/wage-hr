package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wagepayroll.security.BreakGlassHeaders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform SuperAdmin passes tenant {@code @RequiresPrivilege} checks via {@link com.wagepayroll.security.PermissionService}
 * (registered privilege exists globally), even when the tenant role is narrower (Acme Reader).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SuperadminTenantPrivilegeIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void superadminPatchTenantSettingsOnAcmeRequiresBreakGlassReason() throws Exception {
		String body = "{\"entries\":[{\"key\":\"tenant.acme_superadmin_probe\",\"value\":\"1\"}]}";
		mockMvc.perform(
				patch("/api/v1/tenant/settings").header("Host", "acme.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content(body).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void superadminMayPatchTenantSettingsOnAcmeWithBreakGlassHeader() throws Exception {
		String body = "{\"entries\":[{\"key\":\"tenant.acme_superadmin_probe\",\"value\":\"1\"}]}";
		mockMvc.perform(
				patch("/api/v1/tenant/settings").header("Host", "acme.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content(body).header(BreakGlassHeaders.REASON, "Support ticket ACME-42 — adjust probe flag")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isNoContent());
	}
}
