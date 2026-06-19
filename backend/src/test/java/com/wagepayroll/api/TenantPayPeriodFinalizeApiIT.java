package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

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
class TenantPayPeriodFinalizeApiIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String DEMO_HOST = "demo.lvh.me";
	private static final String ANDRE = "5fa00000-0000-4000-8000-000000000006";
	private static final String FEB_2026_PERIOD = "5fa00000-0000-4000-8000-00000000000c";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void finalizeAndListResultLines() throws Exception {
		MvcResult run = mockMvc.perform(post("/api/v1/pay-period-runs")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"payPeriodId\":\"" + FEB_2026_PERIOD + "\",\"runType\":\"FINAL\"}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated())
				.andReturn();
		String runId = JsonPath.read(run.getResponse().getContentAsString(), "$.data.item.id");

		mockMvc.perform(post("/api/v1/pay-periods/{periodId}/runs/{runId}/finalize", FEB_2026_PERIOD, runId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"employeeIds\":[\"" + ANDRE + "\"],\"materializeInputs\":false}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.linesCreated").isNumber())
				.andExpect(jsonPath("$.data.item.employeeCount").value(1));

		mockMvc.perform(get("/api/v1/pay-period-runs/{runId}/result-lines", runId)
						.header("Host", DEMO_HOST)
						.param("employeeId", ANDRE)
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.data").isArray())
				.andExpect(jsonPath("$.data.data[0].roundedAmount").exists());

		mockMvc.perform(post("/api/v1/pay-periods/{periodId}/runs/{runId}/finalize", FEB_2026_PERIOD, runId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"employeeIds\":[\"" + ANDRE + "\"]}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isConflict());
	}

	@Test
	void finalizeForbiddenWithoutPrivilege() throws Exception {
		mockMvc.perform(post("/api/v1/pay-periods/{periodId}/runs/{runId}/finalize", FEB_2026_PERIOD,
						UUID.randomUUID())
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.with(user("30000000-0000-0000-0000-000000000002")).with(csrf()))
				.andExpect(status().isForbidden());
	}
}
