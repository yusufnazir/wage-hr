package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformRoleTemplatesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void nonOperatorForbidden() throws Exception {
		mockMvc.perform(get("/api/v1/platform/role-templates").header("Host", "admin.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void operatorGetsTemplates() throws Exception {
		mockMvc.perform(get("/api/v1/platform/role-templates").header("Host", "admin.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[?(@.code=='ADMIN')]").isArray())
				.andExpect(jsonPath("$.data.items[?(@.code=='EMPLOYEE')]").isArray());
	}

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void operatorMayCreateAndPatchTemplate() throws Exception {
		String createBody = "{\"code\":\"MANAGER\",\"displayName\":\"Manager\",\"privilegeCodes\":[\"USER_VIEW\"]}";
		MvcResult created = mockMvc.perform(
				post("/api/v1/platform/role-templates").header("Host", "admin.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content(createBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.item.code").value("MANAGER"))
				.andReturn();

		String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("item").get("id").asText();

		String patchBody = "{\"displayName\":\"Manager v2\",\"privilegeCodes\":[\"USER_VIEW\",\"ROLE_VIEW\"]}";
		mockMvc.perform(patch("/api/v1/platform/role-templates/" + id).header("Host", "admin.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(patchBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.displayName").value("Manager v2"))
				.andExpect(jsonPath("$.data.item.privilegeCodes").isArray());

		mockMvc.perform(get("/api/v1/platform/role-templates/" + id).header("Host", "admin.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.id").value(id))
				.andExpect(jsonPath("$.data.item.code").value("MANAGER"));
	}
}

