package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class WageComponentFormulaValidateApiIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";
	private static final String DEMO_HOST = "demo.lvh.me";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void tenantValidateFormulaOk() throws Exception {
		String body = """
				{
				  "calculationMethod": "FORMULA",
				  "formulaExpression": "definition.default_amount",
				  "mockContext": {
				    "definitionDefaultAmount": "18500"
				  }
				}
				""";
		mockMvc.perform(post("/api/v1/wage-components/validate-formula")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.ok").value(true))
				.andExpect(jsonPath("$.data.item.amount").value(18500.0000));
	}

	@Test
	void tenantValidateInvalidFormulaReturns400() throws Exception {
		String body = """
				{
				  "calculationMethod": "FORMULA",
				  "formulaExpression": "unknown.ref",
				  "mockContext": {}
				}
				""";
		mockMvc.perform(post("/api/v1/wage-components/validate-formula")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void tenantValidateForbiddenWithoutPrivilege() throws Exception {
		mockMvc.perform(post("/api/v1/wage-components/validate-formula")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"calculationMethod\":\"FIXED_AMOUNT\",\"mockContext\":{}}")
						.with(user(NOCODE_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void platformValidateFormulaRequiresSuperadmin() throws Exception {
		String body = """
				{
				  "calculationMethod": "HOURLY",
				  "mockContext": {
				    "transactionQuantity": "10",
				    "transactionRate": "15.50"
				  }
				}
				""";
		mockMvc.perform(post("/api/v1/platform/wage-component-templates/validate-formula")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.amount").value(155.0000));

		mockMvc.perform(post("/api/v1/platform/wage-component-templates/validate-formula")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}
}
