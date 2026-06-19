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
class DefaultPayrollCatalogProvisioningIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String DEMO_HOST = "demo.lvh.me";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void newSrCompanyGetsDefaultWageComponentsAndComponentGroup() throws Exception {
		String companyId = createCompany("default-catalog");
		mockMvc.perform(get("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.param("size", "100")
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.data.length()").value(26));

		mockMvc.perform(get("/api/v1/component-groups")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.param("size", "20")
						.param("locale", "en")
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].name").value("Default components"));
	}

	private String createCompany(String suffix) throws Exception {
		String taxId = "DC-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6);
		String body = """
				{
				  "name": "DC Co %s",
				  "legalName": "DC Co %s Legal",
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
}
