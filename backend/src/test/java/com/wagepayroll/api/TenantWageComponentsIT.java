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
class TenantWageComponentsIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";
	private static final String DEMO_HOST = "demo.lvh.me";
	private static final String SR_TEMPLATE_BASIC = "51000000-0000-0000-0000-000000000001";
	private static final String SR_TEMPLATE_OVERTIME = "51000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	private String createCompany(String suffix) throws Exception {
		String taxId = "WC-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6);
		String body = """
				{
				  "name": "WC Co %s",
				  "legalName": "WC Co %s Legal",
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
		mockMvc.perform(get("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.with(user(NOCODE_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void viewerCanListCatalogButCannotCreate() throws Exception {
		String companyId = createCompany("viewer");
		mockMvc.perform(get("/api/v1/wage-components/catalog/statutory")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(3));

		String createJson = """
				{"companyId":"%s","platformTemplateId":"%s"}
				""".formatted(companyId, SR_TEMPLATE_BASIC);
		mockMvc.perform(post("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCreatesFromTemplateWithSuffixAndDuplicateRejected() throws Exception {
		String companyId = createCompany("admin");
		String createJson = """
				{"companyId":"%s","platformTemplateId":"%s","codeSuffix":"CUSTOM","name":"Overtime variant"}
				""".formatted(companyId, SR_TEMPLATE_OVERTIME);
		mockMvc.perform(post("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.item.code").value("1002_CUSTOM"))
				.andExpect(jsonPath("$.data.item.templateCode").value("1002"));

		mockMvc.perform(get("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.data.length()").value(1));

		String duplicate = """
				{"companyId":"%s","platformTemplateId":"%s","codeSuffix":"CUSTOM"}
				""".formatted(companyId, SR_TEMPLATE_OVERTIME);
		mockMvc.perform(post("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(duplicate)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("DUPLICATE_CODE"));
	}

	@Test
	void createFromNonDuplicableTemplateRejected() throws Exception {
		String companyId = createCompany("nodup");
		String createJson = """
				{"companyId":"%s","platformTemplateId":"%s"}
				""".formatted(companyId, SR_TEMPLATE_BASIC);
		mockMvc.perform(post("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("TEMPLATE_NOT_DUPLICABLE"));
	}

	@Test
	void adminCanPatchActiveAndPutSlimBody() throws Exception {
		String companyId = createCompany("put");
		String createJson = """
				{"companyId":"%s","platformTemplateId":"%s"}
				""".formatted(companyId, SR_TEMPLATE_OVERTIME);
		MvcResult created = mockMvc.perform(post("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.item.id");

		mockMvc.perform(patch("/api/v1/wage-components/" + id + "/active")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.active").value(false));

		String putBody = """
				{
				  "companyId":"%s",
				  "name":"Renamed base",
				  "codeSuffix":"",
				  "debitTenantLedgerId":null,
				  "creditTenantLedgerId":null,
				  "printOnPayslip":true,
				  "active":true
				}
				""".formatted(companyId);
		mockMvc.perform(put("/api/v1/wage-components/" + id)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.name").value("Renamed base"))
				.andExpect(jsonPath("$.data.item.active").value(true))
				.andExpect(jsonPath("$.data.item.calculationMethod").value("HOURLY"));
	}

	@Test
	void putDoesNotChangeCalculationMethod() throws Exception {
		String companyId = createCompany("calc");
		String createJson = """
				{"companyId":"%s","platformTemplateId":"%s"}
				""".formatted(companyId, SR_TEMPLATE_OVERTIME);
		MvcResult created = mockMvc.perform(post("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.item.id");

		String putBody = """
				{
				  "companyId":"%s",
				  "name":"Only label",
				  "codeSuffix":"",
				  "debitTenantLedgerId":null,
				  "creditTenantLedgerId":null,
				  "printOnPayslip":false,
				  "active":true
				}
				""".formatted(companyId);
		mockMvc.perform(put("/api/v1/wage-components/" + id)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.calculationMethod").value("HOURLY"))
				.andExpect(jsonPath("$.data.item.printOnPayslip").value(false));
	}

	@Test
	void invalidCodeSuffixRejected() throws Exception {
		String companyId = createCompany("suf");
		String createJson = """
				{"companyId":"%s","platformTemplateId":"%s","codeSuffix":"bad space"}
				""".formatted(companyId, SR_TEMPLATE_OVERTIME);
		mockMvc.perform(post("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("INVALID_CODE_SUFFIX"));
	}
}
