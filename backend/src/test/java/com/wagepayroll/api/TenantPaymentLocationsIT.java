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
class TenantPaymentLocationsIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";
	private static final String DEMO_HOST = "demo.lvh.me";

	@Autowired
	private MockMvc mockMvc;

	// ─── helpers ────────────────────────────────────────────────────────────

	private String createCompany(String suffix) throws Exception {
		String taxId = "PL-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6);
		String body = """
				{
				  "name": "PL Co %s",
				  "legalName": "PL Co %s Legal",
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

	// ─── privilege tests ────────────────────────────────────────────────────

	@Test
	void listRequiresCompanyId() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listForbiddenWithoutPrivilege() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.param("companyId", UUID.randomUUID().toString())
						.with(user(NOCODE_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void viewerCanListButCannotMutate() throws Exception {
		String companyId = createCompany("viewer");

		// viewer can list
		mockMvc.perform(get("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk());

		// viewer cannot create
		String createJson = """
				{"companyId":"%s","name":"Cash Desk","paymentType":"CASH","currency":"SRD"}
				""".formatted(companyId);
		mockMvc.perform(post("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	// ─── create CASH ─────────────────────────────────────────────────────────

	@Test
	void createCashPaymentLocation() throws Exception {
		String companyId = createCompany("cash");
		String createJson = """
				{"companyId":"%s","name":"Main Cash Desk","paymentType":"CASH","currency":"SRD"}
				""".formatted(companyId);

		MvcResult r = mockMvc.perform(post("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.item.paymentType").value("CASH"))
				.andExpect(jsonPath("$.data.item.name").value("Main Cash Desk"))
				.andExpect(jsonPath("$.data.item.currency").value("SRD"))
				.andExpect(jsonPath("$.data.item.active").value(true))
				.andReturn();

		String id = JsonPath.read(r.getResponse().getContentAsString(), "$.data.item.id");

		// appears in list
		mockMvc.perform(get("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1));

		// get by id
		mockMvc.perform(get("/api/v1/tenant/payment-locations/" + id)
						.header("Host", DEMO_HOST)
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.id").value(id));
	}

	@Test
	void cashWithBankTemplateIsRejected() throws Exception {
		String companyId = createCompany("cashbt");
		String createJson = """
				{
				  "companyId":"%s",
				  "name":"Cash With BT",
				  "paymentType":"CASH",
				  "currency":"SRD",
				  "bankTemplateId":"%s"
				}
				""".formatted(companyId, UUID.randomUUID());

		mockMvc.perform(post("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void invalidCurrencyIsRejected() throws Exception {
		String companyId = createCompany("curr");
		String createJson = """
				{"companyId":"%s","name":"Bad Currency","paymentType":"CASH","currency":"XYZ1"}
				""".formatted(companyId);

		mockMvc.perform(post("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void duplicateNameIsRejected() throws Exception {
		String companyId = createCompany("dup");
		String createJson = """
				{"companyId":"%s","name":"Cash Desk","paymentType":"CASH","currency":"SRD"}
				""".formatted(companyId);

		mockMvc.perform(post("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated());

		// second with same name
		mockMvc.perform(post("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isConflict());
	}

	// ─── activate/deactivate ─────────────────────────────────────────────────

	@Test
	void deactivateAndActivate() throws Exception {
		String companyId = createCompany("toggle");
		String createJson = """
				{"companyId":"%s","name":"Desk Toggle","paymentType":"CASH","currency":"USD"}
				""".formatted(companyId);

		MvcResult cr = mockMvc.perform(post("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String id = JsonPath.read(cr.getResponse().getContentAsString(), "$.data.item.id");

		// deactivate
		mockMvc.perform(patch("/api/v1/tenant/payment-locations/" + id + "/deactivate")
						.header("Host", DEMO_HOST)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.active").value(false));

		// active-only list excludes it
		mockMvc.perform(get("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.param("active", "true")
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0));

		// reactivate
		mockMvc.perform(patch("/api/v1/tenant/payment-locations/" + id + "/activate")
						.header("Host", DEMO_HOST)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.active").value(true));
	}

	@Test
	void paymentTypeImmutable() throws Exception {
		String companyId = createCompany("immut");
		String createJson = """
				{"companyId":"%s","name":"Immutable Desk","paymentType":"CASH","currency":"SRD"}
				""".formatted(companyId);

		MvcResult cr = mockMvc.perform(post("/api/v1/tenant/payment-locations")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String id = JsonPath.read(cr.getResponse().getContentAsString(), "$.data.item.id");

		// attempt to update with bankTemplateId (violates CASH type constraint)
		String updateJson = """
				{"name":"Immutable Desk","currency":"SRD","bankTemplateId":"%s","accountNumber":"12345678"}
				""".formatted(UUID.randomUUID());

		mockMvc.perform(put("/api/v1/tenant/payment-locations/" + id)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void crossTenantAccessForbidden() throws Exception {
		// Unknown tenant host returns 404 (company not in tenant context)
		mockMvc.perform(get("/api/v1/tenant/payment-locations")
						.header("Host", "unknown-tenant.lvh.me")
						.param("companyId", UUID.randomUUID().toString())
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().is4xxClientError());
	}
}
