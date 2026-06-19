package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformLedgerTemplatesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listForbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/ledger-templates").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void listOkForSuperadminAndContainsSeededSr() throws Exception {
		mockMvc.perform(get("/api/v1/platform/ledger-templates").param("country", "SR").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.items[0].countryCode").value("SR"));
	}

	@Test
	void listWithLocaleNlReturnsDutchDescriptionsForSr() throws Exception {
		mockMvc.perform(get("/api/v1/platform/ledger-templates").param("country", "SR").param("locale", "nl")
				.with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].code").value("1000"))
				.andExpect(jsonPath("$.data.items[0].description").value("Overwerk"));
	}

	@Test
	void postRejectsNonPayrollEnabledCountry() throws Exception {
		String body = """
				{
				  "countryCode": "XX",
				  "code": "X1",
				  "translations": [
				    { "locale": "en", "description": "x" },
				    { "locale": "nl", "description": "x" }
				  ],
				  "active": true
				}
				""";
		mockMvc.perform(post("/api/v1/platform/ledger-templates").contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isUnprocessableEntity());
	}

	@Test
	void crudAndActivateDeactivate() throws Exception {
		String create = """
				{
				  "countryCode": "SR",
				  "code": "IT_TEST_LEDGER",
				  "translations": [
				    { "locale": "en", "description": "Integration test ledger" },
				    { "locale": "nl", "description": "Integratietest grootboek" }
				  ],
				  "active": true
				}
				""";
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/ledger-templates").contentType(MediaType.APPLICATION_JSON).content(create)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.template.code").value("IT_TEST_LEDGER"))
				.andReturn();
		String id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.template.id");

		mockMvc.perform(get("/api/v1/platform/ledger-templates/" + id).with(user(ADMIN_USER_ID))).andExpect(status().isOk());

		String put = """
				{
				  "countryCode": "SR",
				  "code": "IT_TEST_LEDGER",
				  "translations": [
				    { "locale": "en", "description": "Updated" },
				    { "locale": "nl", "description": "Bijgewerkt" }
				  ],
				  "active": true
				}
				""";
		mockMvc.perform(put("/api/v1/platform/ledger-templates/" + id).contentType(MediaType.APPLICATION_JSON).content(put)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.template.description").value("Updated"));

		mockMvc.perform(get("/api/v1/platform/ledger-templates/" + id).param("locale", "nl").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.template.description").value("Bijgewerkt"));

		mockMvc.perform(patch("/api/v1/platform/ledger-templates/" + id + "/deactivate").with(user(ADMIN_USER_ID))
				.with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.template.active").value(false));

		mockMvc.perform(patch("/api/v1/platform/ledger-templates/" + id + "/deactivate").with(user(ADMIN_USER_ID))
				.with(csrf())).andExpect(status().isConflict());

		mockMvc.perform(patch("/api/v1/platform/ledger-templates/" + id + "/activate").with(user(ADMIN_USER_ID))
				.with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.template.active").value(true));
	}
}
