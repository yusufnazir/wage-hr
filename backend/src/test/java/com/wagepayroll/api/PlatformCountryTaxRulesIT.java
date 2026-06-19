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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformCountryTaxRulesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void forbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/country-tax-rules").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void crudAndActivateDeactivate() throws Exception {
		ObjectNode create = objectMapper.createObjectNode();
		create.put("countryCode", "SR");
		create.put("ruleCode", "SR_ADMIN_IT_TAX_RULE");
		create.put("name", "Admin IT test rule");
		create.put("effectiveFrom", "2099-01-01");
		create.putNull("effectiveTo");
		create.put("parametersJson", "{\"v\":2,\"kind\":\"TEST\"}");
		create.put("active", true);
		String createJson = objectMapper.writeValueAsString(create);

		MvcResult created = mockMvc.perform(post("/api/v1/platform/country-tax-rules")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson)
				.with(user(ADMIN_USER_ID))
				.with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.item.countryCode").value("SR"))
				.andExpect(jsonPath("$.data.item.ruleCode").value("SR_ADMIN_IT_TAX_RULE"))
				.andReturn();

		String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.item.id");

		mockMvc.perform(get("/api/v1/platform/country-tax-rules/" + id).with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.name").value("Admin IT test rule"));

		mockMvc.perform(get("/api/v1/platform/country-tax-rules").param("country", "SR").param("search", "ADMIN_IT")
				.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].ruleCode").value("SR_ADMIN_IT_TAX_RULE"));

		ObjectNode put = objectMapper.createObjectNode();
		put.put("name", "Admin IT test rule updated");
		put.put("parametersJson", "{\"v\":3}");
		put.putNull("effectiveTo");
		put.put("active", true);
		String putJson = objectMapper.writeValueAsString(put);

		mockMvc.perform(put("/api/v1/platform/country-tax-rules/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(putJson)
				.with(user(ADMIN_USER_ID))
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.name").value("Admin IT test rule updated"));

		mockMvc.perform(patch("/api/v1/platform/country-tax-rules/" + id + "/deactivate")
				.with(user(ADMIN_USER_ID))
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.active").value(false));

		mockMvc.perform(patch("/api/v1/platform/country-tax-rules/" + id + "/activate")
				.with(user(ADMIN_USER_ID))
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.active").value(true));
	}
}
