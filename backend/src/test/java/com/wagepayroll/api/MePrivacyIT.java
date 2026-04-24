package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class MePrivacyIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void exportReturnsAccountAndMemberships() throws Exception {
		mockMvc.perform(get("/api/v1/me/privacy/export").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.export.exportSchemaVersion").value(MePrivacyController.EXPORT_SCHEMA_VERSION))
				.andExpect(jsonPath("$.data.export.account.email").value("admin@demo.lvh.me"))
				.andExpect(jsonPath("$.data.export.tenantMemberships.length()").value(2));
	}

	@Test
	void erasureRequestAccepted() throws Exception {
		mockMvc.perform(
				post("/api/v1/me/privacy/erasure-request").contentType(MediaType.APPLICATION_JSON).content("{}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.status").value("accepted"));
	}

	@Test
	void erasureRequestRejectsLongNote() throws Exception {
		String longNote = "x".repeat(501);
		mockMvc.perform(
				post("/api/v1/me/privacy/erasure-request").contentType(MediaType.APPLICATION_JSON)
						.content("{\"note\":\"" + longNote + "\"}").with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}
}
