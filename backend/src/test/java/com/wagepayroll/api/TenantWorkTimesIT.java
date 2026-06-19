package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class TenantWorkTimesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";
	private static final String DEMO_HOST = "demo.lvh.me";

	@Autowired
	private MockMvc mockMvc;

	private String createCompany(String suffix) throws Exception {
		String taxId = "WT-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6);
		String body = """
				{
				  "name": "WT Co %s",
				  "legalName": "WT Co %s Legal",
				  "registrationNumber": null,
				  "taxId": "%s",
				  "payrollCountry": "SR",
				  "currency": "SRD",
				  "payrollFrequency": "MONTHLY",
				  "timezone": "America/Paramaribo",
				  "dateFormat": "yyyy-MM-dd",
				  "contactEmail": null,
				  "contactPhone": null,
				  "addressLine1": null,
				  "addressLine2": null,
				  "city": null,
				  "stateRegion": null,
				  "postalCode": null,
				  "country": null,
				  "payPeriodEndDate": "2026-05-31",
				  "timesheetEndDate": "2026-05-31",
				  "active": true
				}
				""".formatted(suffix, suffix, taxId);
		MvcResult r = mockMvc.perform(post("/api/v1/companies")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.item.id");
	}

	@Test
	void listForbiddenWithoutPrivilege() throws Exception {
		mockMvc.perform(get("/api/v1/work-times")
						.header("Host", DEMO_HOST)
						.with(user(NOCODE_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void viewerCanListButCannotMutate() throws Exception {
		String companyId = createCompany("viewer");
		mockMvc.perform(get("/api/v1/work-times")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk());

		String createJson = """
				{"companyId":"%s","name":"Standard 8x5","code":"STD","hoursPerDay":8.00,"workDaysPerWeek":5,"active":true}
				""".formatted(companyId);
		mockMvc.perform(post("/api/v1/work-times")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void createUpdateDeactivateReactivate() throws Exception {
		String companyId = createCompany("happy");

		String createJson = """
				{"companyId":"%s","name":"Standard 8x5","code":"STD","hoursPerDay":8.00,"workDaysPerWeek":5,"description":"Default schedule","active":true}
				""".formatted(companyId);
		MvcResult created = mockMvc.perform(post("/api/v1/work-times")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.item.companyId").value(companyId))
				.andReturn();

		String id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.item.id");

		String putJson = """
				{"companyId":"%s","name":"Standard 8x5 Updated","code":"STD","hoursPerDay":7.50,"workDaysPerWeek":5,"description":"Updated","active":true}
				""".formatted(companyId);
		mockMvc.perform(put("/api/v1/work-times/{id}", id)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(putJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.name").value("Standard 8x5 Updated"));

		mockMvc.perform(patch("/api/v1/work-times/{id}/active", id)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.active").value(false));

		mockMvc.perform(get("/api/v1/work-times")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.param("active", "true")
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.data.length()").value(0));

		mockMvc.perform(patch("/api/v1/work-times/{id}/active", id)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":true}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.active").value(true));
	}
}

