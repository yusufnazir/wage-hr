package com.wagepayroll.api;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MeLocalePatchIT {

	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void patchLocaleUnsupportedReturns400() throws Exception {
		mockMvc.perform(
				patch("/api/v1/me/locale").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content("{\"locale\":\"xx\"}").with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void patchLocaleNlThenMeReturnsNl() throws Exception {
		mockMvc.perform(
				patch("/api/v1/me/locale").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content("{\"locale\":\"nl\"}").with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/me").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.locale").value("nl"));
	}

	@Test
	void patchLocaleRejectsNlSr() throws Exception {
		mockMvc.perform(
				patch("/api/v1/me/locale").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content("{\"locale\":\"nl-sr\"}").with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}
}
