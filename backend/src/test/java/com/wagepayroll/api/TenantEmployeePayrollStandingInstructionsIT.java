package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionEntity;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.catalog.DemoPayrollCatalogInitializer;

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
class TenantEmployeePayrollStandingInstructionsIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";
	private static final String DEMO_HOST = "demo.lvh.me";
	private static final String SR_TEMPLATE_OVERTIME = "51000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TenantWageComponentRepository wageComponentRepository;

	@Autowired
	private TenantEmployeePayrollStandingInstructionRepository standingRepository;

	@Test
	void createEmployeeAutoAssignsActiveCompanyWageComponentsAsStandingInstructions() throws Exception {
		String companyId = createCompany("autosi");
		String dept = createDepartment(companyId, "AS1");
		String grp = createEmployeeGroup(companyId, "AS1");
		String job = createJob(companyId, dept, "AS1");
		String empId = createEmployee(companyId, dept, job, grp);

		UUID tenantId = DemoPayrollCatalogInitializer.DEMO_TENANT_ID;
		UUID companyUuid = UUID.fromString(companyId);
		UUID employeeUuid = UUID.fromString(empId);

		List<TenantWageComponentEntity> eligible = wageComponentRepository
				.findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(tenantId, companyUuid);
		assertThat(eligible).isNotEmpty();

		List<TenantEmployeePayrollStandingInstructionEntity> standings = standingRepository
				.findByTenantIdAndCompanyIdAndEmployeeIdOrderByEffectiveFromAsc(tenantId, companyUuid, employeeUuid);
		assertThat(standings).hasSize(eligible.size());
		assertThat(standings).extracting(TenantEmployeePayrollStandingInstructionEntity::getTenantWageComponentId)
				.containsExactlyInAnyOrderElementsOf(eligible.stream().map(TenantWageComponentEntity::getId).toList());
		assertThat(standings).allMatch(si -> "2025-01-01".equals(si.getEffectiveFrom().toString()));
		assertThat(standings).allMatch(TenantEmployeePayrollStandingInstructionEntity::isActive);
	}

	private String createCompany(String suffix) throws Exception {
		String taxId = "SI-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6);
		String body = """
				{
				  "name": "SI Co %s",
				  "legalName": "SI Co %s Legal",
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

	private String createDepartment(String companyId, String code) throws Exception {
		String body = """
				{"companyId":"%s","name":"Dept %s","code":"%s","description":null,"parentDepartmentId":null,"managerEmployeeId":null,"active":true}
				""".formatted(companyId, code, code);
		MvcResult r = mockMvc.perform(post("/api/v1/departments")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.item.id");
	}

	private String createEmployeeGroup(String companyId, String code) throws Exception {
		String body = """
				{"companyId":"%s","name":"Grp %s","code":"%s","description":null,"active":true}
				""".formatted(companyId, code, code);
		MvcResult r = mockMvc.perform(post("/api/v1/employee-groups")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.item.id");
	}

	private String createJob(String companyId, String departmentId, String code) throws Exception {
		String body = """
				{
				  "companyId":"%s",
				  "departmentId":"%s",
				  "title":"Job %s",
				  "code":"%s",
				  "description":null,
				  "salaryType":"MONTHLY",
				  "defaultSalary":null,
				  "defaultHourlyRate":null,
				  "standardHoursPerWeek":40,
				  "jobLevel":null,
				  "jobCategory":null,
				  "active":true
				}
				""".formatted(companyId, departmentId, code, code);
		MvcResult r = mockMvc.perform(post("/api/v1/jobs")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.item.id");
	}

	private String createEmployee(String companyId, String departmentId, String jobId, String employeeGroupId)
			throws Exception {
		String body = """
				{
				  "companyId":"%s",
				  "departmentId":"%s",
				  "jobId":"%s",
				  "employeeGroupId":"%s",
				  "firstName":"Jane",
				  "lastName":"Doe",
				  "dateOfBirth":null,
				  "hireDate":"2025-01-01",
				  "email":null,
				  "phone":null,
				  "status":"ACTIVE",
				  "active":true
				}
				""".formatted(companyId, departmentId, jobId, employeeGroupId);
		MvcResult r = mockMvc.perform(post("/api/v1/employees")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.item.id");
	}

	private String createWageComponent(String companyId) throws Exception {
		String body = """
				{"companyId":"%s","platformTemplateId":"%s","codeSuffix":"SI_ALLOW","name":"Standing allowance"}
				""".formatted(companyId, SR_TEMPLATE_OVERTIME);
		MvcResult r = mockMvc.perform(post("/api/v1/wage-components")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.item.id");
	}

	private String createPayPeriod(String companyId) throws Exception {
		String body = """
				{"companyId":"%s","year":2026,"startDate":"2026-05-01","endDate":"2026-05-31","status":"READY"}
				""".formatted(companyId);
		MvcResult r = mockMvc.perform(post("/api/v1/pay-periods")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.item.id");
	}

	@Test
	void listForbiddenWithoutPrivilege() throws Exception {
		String companyId = createCompany("nopriv");
		mockMvc.perform(get("/api/v1/payroll-standing-instructions")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.param("employeeId", UUID.randomUUID().toString())
						.with(user(NOCODE_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void viewerCanListButCannotPostStandingInstruction() throws Exception {
		String companyId = createCompany("viewer");
		String dept = createDepartment(companyId, "V1");
		String grp = createEmployeeGroup(companyId, "V1");
		String job = createJob(companyId, dept, "V1");
		String emp = createEmployee(companyId, dept, job, grp);
		String wc = createWageComponent(companyId);

		mockMvc.perform(get("/api/v1/payroll-standing-instructions")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.param("employeeId", emp)
						.with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk());

		String createJson = """
				{"companyId":"%s","employeeId":"%s","tenantWageComponentId":"%s","effectiveFrom":"2026-01-01","effectiveTo":null,"amount":"100.0000","quantity":null,"rate":null,"recurrence":"EACH_PAY_PERIOD","remarks":null}
				""".formatted(companyId, emp, wc);
		mockMvc.perform(post("/api/v1/payroll-standing-instructions")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson)
						.with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void materializeHappyPathAndIdempotencyAndManualOverridePreserved() throws Exception {
		String companyId = createCompany("happy");
		String dept = createDepartment(companyId, "H1");
		String grp = createEmployeeGroup(companyId, "H1");
		String job = createJob(companyId, dept, "H1");
		String emp = createEmployee(companyId, dept, job, grp);
		String wc = createWageComponent(companyId);
		String payPeriodId = createPayPeriod(companyId);

		String siBody = """
				{"companyId":"%s","employeeId":"%s","tenantWageComponentId":"%s","effectiveFrom":"2026-01-01","effectiveTo":null,"amount":"250.5000","quantity":null,"rate":null,"recurrence":"EACH_PAY_PERIOD","remarks":null}
				""".formatted(companyId, emp, wc);
		mockMvc.perform(post("/api/v1/payroll-standing-instructions")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(siBody)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/pay-periods/{id}/materialize-payroll-inputs", payPeriodId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"companyId\":\"" + companyId + "\"}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.created").value(1))
				.andExpect(jsonPath("$.data.item.updated").value(0));

		mockMvc.perform(post("/api/v1/pay-periods/{id}/materialize-payroll-inputs", payPeriodId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"companyId\":\"" + companyId + "\"}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.created").value(0))
				.andExpect(jsonPath("$.data.item.updated").value(0));

		MvcResult listTx = mockMvc.perform(get("/api/v1/wage-component-transactions")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.param("payPeriodId", payPeriodId)
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.data.length()").value(1))
				.andReturn();
		String txId = JsonPath.read(listTx.getResponse().getContentAsString(), "$.data.data[0].id");

		mockMvc.perform(put("/api/v1/wage-component-transactions/" + txId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\":\"999.0000\",\"manualOverride\":true,\"remarks\":\"override\"}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.manualOverride").value(true))
				.andExpect(jsonPath("$.data.item.amount").value(999.0000));

		mockMvc.perform(post("/api/v1/pay-periods/{id}/materialize-payroll-inputs", payPeriodId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"companyId\":\"" + companyId + "\"}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.item.skippedManualOverride").value(1));

		mockMvc.perform(get("/api/v1/wage-component-transactions")
						.header("Host", DEMO_HOST)
						.param("companyId", companyId)
						.param("payPeriodId", payPeriodId)
						.with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.data[0].amount").value(999.0000));
	}

	@Test
	void overlappingStandingInstructionsRejected() throws Exception {
		String companyId = createCompany("overlap");
		String dept = createDepartment(companyId, "O1");
		String grp = createEmployeeGroup(companyId, "O1");
		String job = createJob(companyId, dept, "O1");
		String emp = createEmployee(companyId, dept, job, grp);
		String wc = createWageComponent(companyId);

		String first = """
				{"companyId":"%s","employeeId":"%s","tenantWageComponentId":"%s","effectiveFrom":"2026-01-01","effectiveTo":"2026-06-30","amount":"100.0000","quantity":null,"rate":null,"recurrence":"EACH_PAY_PERIOD","remarks":null}
				""".formatted(companyId, emp, wc);
		mockMvc.perform(post("/api/v1/payroll-standing-instructions")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(first)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated());

		String second = """
				{"companyId":"%s","employeeId":"%s","tenantWageComponentId":"%s","effectiveFrom":"2026-06-01","effectiveTo":null,"amount":"200.0000","quantity":null,"rate":null,"recurrence":"EACH_PAY_PERIOD","remarks":null}
				""".formatted(companyId, emp, wc);
		mockMvc.perform(post("/api/v1/payroll-standing-instructions")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(second)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isConflict());
	}

	@Test
	void wageComponentFromAnotherCompanyRejected() throws Exception {
		String companyA = createCompany("acomp");
		String companyB = createCompany("bcomp");
		String wcB = createWageComponent(companyB);

		String dept = createDepartment(companyA, "X1");
		String grp = createEmployeeGroup(companyA, "X1");
		String job = createJob(companyA, dept, "X1");
		String emp = createEmployee(companyA, dept, job, grp);

		String body = """
				{"companyId":"%s","employeeId":"%s","tenantWageComponentId":"%s","effectiveFrom":"2026-01-01","effectiveTo":null,"amount":"100.0000","quantity":null,"rate":null,"recurrence":"EACH_PAY_PERIOD","remarks":null}
				""".formatted(companyA, emp, wcB);
		mockMvc.perform(post("/api/v1/payroll-standing-instructions")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void inactiveWageComponentBlocksMaterialization() throws Exception {
		String companyId = createCompany("inactivewc");
		String dept = createDepartment(companyId, "I1");
		String grp = createEmployeeGroup(companyId, "I1");
		String job = createJob(companyId, dept, "I1");
		String emp = createEmployee(companyId, dept, job, grp);
		String wc = createWageComponent(companyId);
		String payPeriodId = createPayPeriod(companyId);

		String siBody = """
				{"companyId":"%s","employeeId":"%s","tenantWageComponentId":"%s","effectiveFrom":"2026-01-01","effectiveTo":null,"amount":"50.0000","quantity":null,"rate":null,"recurrence":"EACH_PAY_PERIOD","remarks":null}
				""".formatted(companyId, emp, wc);
		mockMvc.perform(post("/api/v1/payroll-standing-instructions")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content(siBody)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated());

		mockMvc.perform(patch("/api/v1/wage-components/" + wc + "/active")
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/pay-periods/{id}/materialize-payroll-inputs", payPeriodId)
						.header("Host", DEMO_HOST)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"companyId\":\"" + companyId + "\"}")
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isConflict());
	}
}
