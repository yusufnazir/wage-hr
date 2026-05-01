package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
class TenantExchangeRatesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";


	@Autowired
	private MockMvc mockMvc;

	@Test
	void listDeniedWithoutExchangeRateView() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/exchange-rates").header("Host", "acme.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void viewerCanListOnDemoTenant() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/exchange-rates").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.data").isArray())
				.andExpect(jsonPath("$.data.page.number").value(0));
	}

	@Test
	void createResolvePatchAndDeleteLifecycle() throws Exception {
		String[] pair = assignedCurrencyPair();
		String fromCurrencyId = pair[0];
		String toCurrencyId = pair[1];
		String fromCode = pair[2];
		String toCode = pair[3];
		String createBody = """
				{
				  "fromCurrencyId": "%s",
				  "toCurrencyId": "%s",
				  "rate": "0.92500000",
				  "effectiveDate": "2026-05-01"
				}
				""".formatted(fromCurrencyId, toCurrencyId);

		MvcResult created = mockMvc
				.perform(post("/api/v1/tenant/exchange-rates").header("Host", "demo.lvh.me")
						.contentType(MediaType.APPLICATION_JSON).content(createBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.item.id").isNotEmpty())
				.andExpect(jsonPath("$.data.item.fromCurrencyCode").value(fromCode))
				.andExpect(jsonPath("$.data.item.toCurrencyCode").value(toCode)).andReturn();

		String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.item.id");

		mockMvc.perform(get("/api/v1/tenant/exchange-rates/resolve").header("Host", "demo.lvh.me")
				.param("from", fromCode).param("to", toCode).param("date", "2026-05-15")
				.with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.fromCurrencyCode").value(fromCode))
				.andExpect(jsonPath("$.data.rate").value(0.92500000));

		mockMvc.perform(
				patch("/api/v1/tenant/exchange-rates/" + id).header("Host", "demo.lvh.me")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rate\":\"0.93000000\",\"effectiveDate\":\"2026-06-01\"}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.item.rate").value(0.93))
				.andExpect(jsonPath("$.data.item.effectiveDate").value("2026-06-01"));

		mockMvc.perform(delete("/api/v1/tenant/exchange-rates/" + id).header("Host", "demo.lvh.me")
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/tenant/exchange-rates/" + id).header("Host", "demo.lvh.me")
				.with(user(VIEWER_USER_ID))).andExpect(status().isNotFound());
	}

	@Test
	void createRejectsSameCurrencyPair() throws Exception {
		String[] pair = assignedCurrencyPair();
		String body = """
				{
				  "fromCurrencyId": "%s",
				  "toCurrencyId": "%s",
				  "rate": "1.00000000",
				  "effectiveDate": "2026-05-01"
				}
				""".formatted(pair[0], pair[0]);
		mockMvc.perform(post("/api/v1/tenant/exchange-rates").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void patchRejectsImmutableCurrencyFields() throws Exception {
		String[] pair = assignedCurrencyPair();
		String createBody = """
				{
				  "fromCurrencyId": "%s",
				  "toCurrencyId": "%s",
				  "rate": "0.92500000",
				  "effectiveDate": "2026-05-01"
				}
				""".formatted(pair[0], pair[1]);

		MvcResult created = mockMvc
				.perform(post("/api/v1/tenant/exchange-rates").header("Host", "demo.lvh.me")
						.contentType(MediaType.APPLICATION_JSON).content(createBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.item.id");

		mockMvc.perform(patch("/api/v1/tenant/exchange-rates/" + id).header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content("{\"fromCurrencyId\":\"" + pair[1] + "\"}")
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void viewerCannotCreate() throws Exception {
		String[] pair = assignedCurrencyPair();
		String body = """
				{
				  "fromCurrencyId": "%s",
				  "toCurrencyId": "%s",
				  "rate": "0.92500000",
				  "effectiveDate": "2026-05-01"
				}
				""".formatted(pair[0], pair[1]);
		mockMvc.perform(post("/api/v1/tenant/exchange-rates").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@SuppressWarnings("unchecked")
	private String[] assignedCurrencyPair() throws Exception {
		mockMvc.perform(put("/api/v1/tenant/currencies").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content("{\"codes\":[\"USD\",\"EUR\"]}")
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNoContent());

		MvcResult result = mockMvc.perform(get("/api/v1/tenant/currencies").header("Host", "demo.lvh.me")
				.with(user(ADMIN_USER_ID))).andExpect(status().isOk()).andReturn();
		String json = result.getResponse().getContentAsString();
		List<Map<String, Object>> items = com.jayway.jsonpath.JsonPath.read(json, "$.data.items");
		List<Map<String, Object>> assigned = new ArrayList<>();
		for (Map<String, Object> item : items) {
			if (Boolean.TRUE.equals(item.get("assigned"))) {
				assigned.add(item);
			}
		}
		if (assigned.size() < 2) {
			throw new IllegalStateException("Expected at least two assigned tenant currencies for test setup");
		}
		return new String[] { (String) assigned.get(0).get("id"), (String) assigned.get(1).get("id"),
				(String) assigned.get(0).get("code"), (String) assigned.get(1).get("code") };
	}
}
