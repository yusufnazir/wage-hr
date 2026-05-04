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
class TenantBankTemplatesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listRequiresCompanyId() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/bank-templates").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listForbiddenWithoutPrivilege() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/bank-templates").header("Host", "demo.lvh.me").param("companyId",
				UUID.randomUUID().toString()).with(user(NOCODE_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void companyCreateCopiesSrTemplates() throws Exception {
		String taxId = "SR-IT-" + UUID.randomUUID().toString().substring(0, 8);
		String companyJson = """
				{
				  "name": "Copy Co",
				  "legalName": "Copy Co Legal",
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
				""".formatted(taxId);

		MvcResult companyRes = mockMvc
				.perform(post("/api/v1/companies").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content(companyJson).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String companyId = com.jayway.jsonpath.JsonPath.read(companyRes.getResponse().getContentAsString(),
				"$.data.item.id");

		mockMvc.perform(get("/api/v1/tenant/bank-templates").header("Host", "demo.lvh.me")
				.param("companyId", companyId).with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(4));
	}

	@Test
	void viewerCanListButCannotMutate() throws Exception {
		String taxId = "SR-VW-" + UUID.randomUUID().toString().substring(0, 8);
		String companyJson = """
				{
				  "name": "View Co",
				  "legalName": "View Co Legal",
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
				""".formatted(taxId);
		MvcResult companyRes = mockMvc
				.perform(post("/api/v1/companies").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content(companyJson).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String companyId = com.jayway.jsonpath.JsonPath.read(companyRes.getResponse().getContentAsString(),
				"$.data.item.id");

		MvcResult listRes = mockMvc
				.perform(get("/api/v1/tenant/bank-templates").header("Host", "demo.lvh.me")
						.param("companyId", companyId).with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk()).andReturn();
		String templateId = com.jayway.jsonpath.JsonPath.read(listRes.getResponse().getContentAsString(),
				"$.data.items[0].id");

		String put = """
				{
				  "name": "X",
				  "bankName": null,
				  "swiftBic": null,
				  "bankCode": null,
				  "accountNumberFormat": null,
				  "currencyCode": null,
				  "active": true
				}
				""";
		mockMvc.perform(put("/api/v1/tenant/bank-templates/" + templateId).header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(put).with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminUpdatesAndPatches() throws Exception {
		String taxId = "SR-AD-" + UUID.randomUUID().toString().substring(0, 8);
		String companyJson = """
				{
				  "name": "Admin Co",
				  "legalName": "Admin Co Legal",
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
				""".formatted(taxId);
		MvcResult companyRes = mockMvc
				.perform(post("/api/v1/companies").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content(companyJson).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String companyId = com.jayway.jsonpath.JsonPath.read(companyRes.getResponse().getContentAsString(),
				"$.data.item.id");

		MvcResult listRes = mockMvc
				.perform(get("/api/v1/tenant/bank-templates").header("Host", "demo.lvh.me")
						.param("companyId", companyId).with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andReturn();
		String templateId = com.jayway.jsonpath.JsonPath.read(listRes.getResponse().getContentAsString(),
				"$.data.items[0].id");

		String put = """
				{
				  "name": "Renamed",
				  "bankName": null,
				  "swiftBic": null,
				  "bankCode": null,
				  "accountNumberFormat": null,
				  "currencyCode": null,
				  "active": false
				}
				""";
		mockMvc.perform(put("/api/v1/tenant/bank-templates/" + templateId).header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(put).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.template.name").value("Renamed"))
				.andExpect(jsonPath("$.data.template.active").value(false));

		mockMvc.perform(patch("/api/v1/tenant/bank-templates/" + templateId + "/deactivate").header("Host", "demo.lvh.me")
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isConflict());

		mockMvc.perform(patch("/api/v1/tenant/bank-templates/" + templateId + "/activate").header("Host", "demo.lvh.me")
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.template.active").value(true));

		mockMvc.perform(patch("/api/v1/tenant/bank-templates/" + templateId + "/activate").header("Host", "demo.lvh.me")
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isConflict());
	}
}
