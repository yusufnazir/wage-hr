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
class TenantPayPeriodsIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";
	private static final String DEMO_HOST = "demo.lvh.me";

	@Autowired
	private MockMvc mockMvc;

	private String createCompany(String suffix) throws Exception {
		String taxId = "PP-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6);
		String body = """
				{
				  "name": "PP Co %s",
				  "legalName": "PP Co %s Legal",
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
		mockMvc.perform(get("/api/v1/pay-periods")
						.header("Host", DEMO_HOST)
						.with(user(NOCODE_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void viewerCanListButCannotMutate() throws Exception {
		String companyId = createCompany("viewer");
		mockMvc.perform(get("/api/v1/pay-periods")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk());

		String createJson = """
				{"companyId":"%s","year":2026,"startDate":"2026-05-01","endDate":"2026-05-31","status":"READY"}
				""".formatted(companyId);
		mockMvc.perform(post("/api/v1/pay-periods")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void createPatchStatusCreateRunAndListRuns() throws Exception {
		String companyId = createCompany("happy");

		String createJson = """
				{"companyId":"%s","year":2026,"startDate":"2026-05-01","endDate":"2026-05-31","status":"READY"}
				""".formatted(companyId);
		MvcResult created = mockMvc.perform(post("/api/v1/pay-periods")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.item.companyId").value(companyId))
				.andReturn();

		String payPeriodId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.item.id");

		mockMvc.perform(patch("/api/v1/pay-periods/{id}/status", payPeriodId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"OPEN\"}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.status").value("OPEN"));

		MvcResult run = mockMvc.perform(post("/api/v1/pay-period-runs")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"payPeriodId\":\"" + payPeriodId + "\",\"runType\":\"INTERIM\"}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.item.payPeriodId").value(payPeriodId))
				.andReturn();

		int runNumber = JsonPath.read(run.getResponse().getContentAsString(), "$.data.item.runNumber");
		mockMvc.perform(get("/api/v1/pay-periods/{id}/runs", payPeriodId)
						.header("Host", DEMO_HOST)
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.data[0].runNumber").value(runNumber));
	}

	@Test
	void generateEndpointReturnsCreatedCount() throws Exception {
		String companyId = createCompany("gen");
		mockMvc.perform(post("/api/v1/companies/{id}/pay-periods/generate", companyId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"yearsAhead\":1}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.created").isNumber());
	}

	@Test
	void formulaPreviewForbiddenWithoutPrivilege() throws Exception {
		mockMvc.perform(post("/api/v1/pay-periods/{id}/formula-preview", UUID.randomUUID())
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"employeeIds\":[\"30000000-0000-0000-0000-000000000099\"]}")
						.with(user(NOCODE_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void formulaPreviewRejectsEmptyEmployeeIds() throws Exception {
		String companyId = createCompany("fprev");
		String createJson = """
				{"companyId":"%s","year":2026,"startDate":"2026-06-01","endDate":"2026-06-30","status":"READY"}
				""".formatted(companyId);
		MvcResult created = mockMvc.perform(post("/api/v1/pay-periods")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated())
				.andReturn();
		String payPeriodId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.item.id");
		mockMvc.perform(post("/api/v1/pay-periods/{id}/formula-preview", payPeriodId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"employeeIds\":[]}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void formulaPreviewOkForViewer() throws Exception {
		// Use demo seed data: materialize runs in REQUIRES_NEW and cannot see pay periods created in this @Transactional test.
		String payPeriodId = "5fa00000-0000-4000-8000-00000000000c";
		String employeeId = "5fa00000-0000-4000-8000-000000000006";
		mockMvc.perform(post("/api/v1/pay-periods/{id}/formula-preview", payPeriodId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"employeeIds\":[\"" + employeeId + "\"]}")
						.with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.employeeBaseTotals").isMap())
				.andExpect(jsonPath("$.data.employeeNetPay").isMap())
				.andExpect(jsonPath("$.data.employeeArt17AttributionPeriods").isMap())
				.andExpect(jsonPath("$.data.employeeCalculationTraceText." + employeeId).isString())
				.andExpect(jsonPath("$.data.employeeCalculationTraceLines." + employeeId).isArray());
	}
}

