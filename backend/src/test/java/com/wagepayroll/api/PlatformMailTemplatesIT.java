package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformMailTemplatesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final UUID SEED_TEMPLATE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void listForbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/mail-templates").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void listContainsSeededInvitationTemplate() throws Exception {
		mockMvc.perform(get("/api/v1/platform/mail-templates").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(3))
				.andExpect(jsonPath("$.data.items[?(@.code=='TENANT_INVITATION')].code").exists())
				.andExpect(jsonPath("$.data.items[?(@.code=='EMAIL_VERIFICATION')].code").exists())
				.andExpect(jsonPath("$.data.items[?(@.code=='PASSWORD_RESET_REQUEST')].code").exists());
	}

	@Test
	void putConflictWhenStaleIfUpdatedAt() throws Exception {
		MvcResult one = mockMvc.perform(get("/api/v1/platform/mail-templates/" + SEED_TEMPLATE_ID).with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andReturn();
		JsonNode item = objectMapper.readTree(one.getResponse().getContentAsString()).get("data").get("item");
		String updatedAt = item.get("updatedAt").asText();
		String badIf = "1970-01-01T00:00:00Z";
		String body = """
				{"ifUpdatedAt":"%s","active":true,"locales":[
				  {"locale":"en","subject":"Invitation: {{tenantHandle}}","bodyHtml":"<p>en</p>"},
				  {"locale":"nl","subject":"Uitnodiging: {{tenantHandle}}","bodyHtml":"<p>nl</p>"}
				]}""".formatted(badIf);
		mockMvc.perform(put("/api/v1/platform/mail-templates/" + SEED_TEMPLATE_ID).contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isConflict());

		String goodPut = """
				{"ifUpdatedAt":"%s","active":true,"locales":[
				  {"locale":"en","subject":"Invitation: {{tenantHandle}}","bodyHtml":"<p>en-updated</p>"},
				  {"locale":"nl","subject":"Uitnodiging: {{tenantHandle}}","bodyHtml":"<p>nl-updated</p>"}
				]}""".formatted(updatedAt);
		mockMvc.perform(put("/api/v1/platform/mail-templates/" + SEED_TEMPLATE_ID).contentType(MediaType.APPLICATION_JSON)
				.content(goodPut).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/platform/mail-templates/" + SEED_TEMPLATE_ID).with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.locales[0].bodyHtml").value("<p>en-updated</p>"));
	}
}
