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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformCountriesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void platformEndpointsForbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/countries").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void platformCrudAndActivateDeactivateFlow() throws Exception {
		String createJson = """
				{
				  "isoAlpha2": "ZZ",
				  "isoAlpha3": "ZZZ",
				  "isoNumeric": "999",
				  "dialCode": "+999",
				  "active": true,
				  "translations": [
				    { "locale": "en", "name": "Zeta Zone" },
				    { "locale": "nl", "name": "Zeta Zone NL" }
				  ]
				}
				""";

		MvcResult created = mockMvc.perform(post("/api/v1/platform/countries")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson)
				.with(user(ADMIN_USER_ID))
				.with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.item.isoAlpha2").value("ZZ"))
				.andExpect(jsonPath("$.data.item.translations.length()").value(2))
				.andReturn();

		String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.item.id");

		mockMvc.perform(get("/api/v1/platform/countries/" + id).with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.name").value("Zeta Zone"));

		String putJson = """
				{
				  "isoAlpha2": "ZZ",
				  "isoAlpha3": "ZZX",
				  "isoNumeric": "998",
				  "dialCode": "+998",
				  "active": true,
				  "translations": [
				    { "locale": "en", "name": "Zeta Zone Updated" },
				    { "locale": "nl", "name": "Zeta Zone Bijgewerkt" }
				  ]
				}
				""";

		mockMvc.perform(put("/api/v1/platform/countries/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(putJson)
				.with(user(ADMIN_USER_ID))
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.isoAlpha3").value("ZZX"))
				.andExpect(jsonPath("$.data.item.name").value("Zeta Zone Updated"));

		mockMvc.perform(patch("/api/v1/platform/countries/" + id + "/deactivate")
				.with(user(ADMIN_USER_ID))
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.active").value(false));

		mockMvc.perform(patch("/api/v1/platform/countries/" + id + "/activate")
				.with(user(ADMIN_USER_ID))
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.active").value(true));
	}

	@Test
	void tenantReadOnlyListReturnsActiveOnlyAndLocaleFallback() throws Exception {
		String createJson = """
				{
				  "isoAlpha2": "ZY",
				  "isoAlpha3": "ZYY",
				  "isoNumeric": "997",
				  "dialCode": "+997",
				  "active": true,
				  "translations": [
				    { "locale": "en", "name": "Zeta Yard" },
				    { "locale": "nl", "name": "Zeta Erf" }
				  ]
				}
				""";
		MvcResult created = mockMvc.perform(post("/api/v1/platform/countries")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson)
				.with(user(ADMIN_USER_ID))
				.with(csrf()))
				.andExpect(status().isCreated())
				.andReturn();
		String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.item.id");

		mockMvc.perform(get("/api/v1/countries").header("Host", "demo.lvh.me").param("locale", "nl")
				.param("search", "ZY")
				.with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.isoAlpha2=='ZY')].name").value(org.hamcrest.Matchers.hasItem("Zeta Erf")));

		mockMvc.perform(get("/api/v1/countries").header("Host", "demo.lvh.me").param("locale", "nl-sr")
				.param("search", "ZY")
				.with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.isoAlpha2=='ZY')].name").value(org.hamcrest.Matchers.hasItem("Zeta Erf")));

		mockMvc.perform(patch("/api/v1/platform/countries/" + id + "/deactivate")
				.with(user(ADMIN_USER_ID))
				.with(csrf()))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/countries").header("Host", "demo.lvh.me").param("locale", "en")
				.param("search", "ZY")
				.with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.isoAlpha2=='ZY')]").isEmpty());
	}

	@Test
	void tenantReadOnlyListRejectsUnsupportedLocale() throws Exception {
		mockMvc.perform(get("/api/v1/countries").header("Host", "demo.lvh.me").param("locale", "fr")
				.with(user(VIEWER_USER_ID)))
				.andExpect(status().isBadRequest());
	}
}
