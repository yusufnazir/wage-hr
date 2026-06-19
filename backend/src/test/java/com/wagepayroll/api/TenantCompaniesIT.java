package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration coverage for the company create flow's currency handling. The demo tenant ships with no
 * {@code tenant_currency} rows, so these tests also exercise the auto-link side-effect end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantCompaniesIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String DEMO_HOST = "demo.lvh.me";

	@Autowired
	private MockMvc mockMvc;

	private String companyJson(String suffix, String currency) {
		String taxId = "CO-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6);
		return """
				{
				  "name": "Co %s",
				  "legalName": "Co %s Legal",
				  "registrationNumber": null,
				  "taxId": "%s",
				  "payrollCountry": "SR",
				  "currency": "%s",
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
				""".formatted(suffix, suffix, taxId, currency);
	}

	@Test
	void createWithUnassignedPlatformCurrencyAutoLinksTenantCurrency() throws Exception {
		// EUR exists in the platform catalog and is NOT assigned to demo tenant by default.
		mockMvc.perform(post("/api/v1/companies")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(companyJson("eur", "EUR"))
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.item.currency").value("EUR"));

		// After create, the tenant currency listing must include EUR among its assignedCodes.
		mockMvc.perform(get("/api/v1/tenant/currencies")
						.header("Host", DEMO_HOST)
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignedCodes[?(@=='EUR')]").exists());
	}

	@Test
	void createWithUnknownPlatformCurrencyReturnsBadRequest() throws Exception {
		// XYZ is a syntactically valid ISO-3 string but is not present in the platform_currency catalog.
		mockMvc.perform(post("/api/v1/companies")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(companyJson("unknown", "XYZ"))
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}
}
