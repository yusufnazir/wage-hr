package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformComponentGroupTemplatesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	private static final UUID SR_COUNTRY_ID = UUID.nameUUIDFromBytes("platform-country:SR".getBytes(StandardCharsets.UTF_8));
	private static final String SR_WAGE_COMPONENT_TEMPLATE_ID = "51000000-0000-0000-0000-000000000013";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void forbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/component-group-templates").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void listOkForSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/component-group-templates").param("country", "SR").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray());
	}

	@Test
	void unsupportedLocaleReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/platform/component-group-templates").param("locale", "fr").with(user(ADMIN_USER_ID)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void crudGroupHeaderItemAndCountryMismatch() throws Exception {
		String createGroup = """
				{
				  "platformCountryId": "%s",
				  "sortOrder": 1,
				  "active": true,
				  "translations": [
				    { "locale": "en", "name": "IT group", "description": "d" },
				    { "locale": "nl", "name": "IT groep", "description": null }
				  ]
				}
				""".formatted(SR_COUNTRY_ID);
		MvcResult g = mockMvc
				.perform(post("/api/v1/platform/component-group-templates").contentType(MediaType.APPLICATION_JSON).content(createGroup)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.group.countryCode").value("SR")).andReturn();
		String groupId = JsonPath.read(g.getResponse().getContentAsString(), "$.data.group.id");

		String createHeader = """
				{
				  "sortOrder": 0,
				  "translations": [
				    { "locale": "en", "name": "IT header", "description": null },
				    { "locale": "nl", "name": "IT kop", "description": null }
				  ]
				}
				""";
		MvcResult h = mockMvc
				.perform(post("/api/v1/platform/component-group-templates/" + groupId + "/headers").contentType(MediaType.APPLICATION_JSON)
						.content(createHeader).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String headerId = JsonPath.read(h.getResponse().getContentAsString(), "$.data.header.id");

		String createItem = """
				{
				  "platformWageComponentTemplateId": "%s",
				  "sortOrder": 0,
				  "translations": [
				    { "locale": "en", "name": "IT item", "description": null },
				    { "locale": "nl", "name": "IT item nl", "description": null }
				  ]
				}
				""".formatted(SR_WAGE_COMPONENT_TEMPLATE_ID);
		MvcResult it = mockMvc
				.perform(post("/api/v1/platform/component-group-templates/" + groupId + "/headers/" + headerId + "/items")
						.contentType(MediaType.APPLICATION_JSON).content(createItem).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.item.wageComponentCode").value("1019")).andReturn();
		String itemId = JsonPath.read(it.getResponse().getContentAsString(), "$.data.item.id");

		mockMvc.perform(get("/api/v1/platform/component-group-templates/" + groupId).param("locale", "nl").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.group.name").value("IT groep"));

		String badWage = "50000000-0000-0000-0000-00000000ffff";
		String badItem = """
				{
				  "platformWageComponentTemplateId": "%s",
				  "sortOrder": 1,
				  "translations": [
				    { "locale": "en", "name": "x", "description": null },
				    { "locale": "nl", "name": "x", "description": null }
				  ]
				}
				""".formatted(badWage);
		mockMvc.perform(post("/api/v1/platform/component-group-templates/" + groupId + "/headers/" + headerId + "/items")
				.contentType(MediaType.APPLICATION_JSON).content(badItem).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());

		mockMvc.perform(delete("/api/v1/platform/component-group-templates/" + groupId + "/headers/" + headerId + "/items/" + itemId)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/platform/component-group-templates/" + groupId + "/headers/" + headerId)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/platform/component-group-templates/" + groupId).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isNoContent());
	}
}
