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
class PlatformBankTemplatesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listForbiddenForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/bank-templates").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void listOkForSuperadminAndContainsSeededSr() throws Exception {
		mockMvc.perform(get("/api/v1/platform/bank-templates").param("country", "SR").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.items[0].countryCode").value("SR"));
	}

	@Test
	void postRejectsNonPayrollEnabledCountry() throws Exception {
		String body = """
				{
				  "countryCode": "XX",
				  "name": "Test Bank",
				  "active": true
				}
				""";
		mockMvc.perform(post("/api/v1/platform/bank-templates").contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isUnprocessableEntity());
	}

	@Test
	void postRejectsInvalidSwift() throws Exception {
		String body = """
				{
				  "countryCode": "SR",
				  "name": "Test Bank",
				  "swiftBic": "bad",
				  "active": true
				}
				""";
		mockMvc.perform(post("/api/v1/platform/bank-templates").contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void crudActivateDeactivateAndPutRejectsUnknownCountryField() throws Exception {
		String create = """
				{
				  "countryCode": "SR",
				  "name": "IT Test Template",
				  "bankName": "Test Bank NV",
				  "swiftBic": "TESTSR22",
				  "active": true
				}
				""";
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/bank-templates").contentType(MediaType.APPLICATION_JSON).content(create)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.template.name").value("IT Test Template"))
				.andReturn();
		String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.template.id");

		mockMvc.perform(get("/api/v1/platform/bank-templates/" + id).with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.template.id").value(id));

		String put = """
				{
				  "name": "IT Test Template Updated",
				  "bankName": "Test Bank NV",
				  "swiftBic": "TESTSR22",
				  "bankCode": "001",
				  "accountNumberFormat": null,
				  "active": true
				}
				""";
		mockMvc.perform(put("/api/v1/platform/bank-templates/" + id).contentType(MediaType.APPLICATION_JSON).content(put)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.template.name").value("IT Test Template Updated"));

		mockMvc.perform(patch("/api/v1/platform/bank-templates/" + id + "/deactivate").with(user(ADMIN_USER_ID))
				.with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.template.active").value(false));

		mockMvc.perform(patch("/api/v1/platform/bank-templates/" + id + "/deactivate").with(user(ADMIN_USER_ID))
				.with(csrf())).andExpect(status().isConflict());

		mockMvc.perform(patch("/api/v1/platform/bank-templates/" + id + "/activate").with(user(ADMIN_USER_ID))
				.with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.template.active").value(true));

		mockMvc.perform(patch("/api/v1/platform/bank-templates/" + id + "/activate").with(user(ADMIN_USER_ID))
				.with(csrf())).andExpect(status().isConflict());
	}
}
