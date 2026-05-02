package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.navmenu.NavMenuItemRepository;
import com.wagepayroll.domain.plan.PlanFeatureRepository;

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
class NavigationAndSettingsIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String DEMO_TENANT_ID = "10000000-0000-0000-0000-000000000001";
	private static final UUID NAV_DASH_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NavMenuItemRepository navMenuItemRepository;

	@Autowired
	private PlanFeatureRepository planFeatureRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void navigationReturns400WhenTenantContextMissingForNonSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/me/navigation").header("Host", "localhost:8300").with(user(VIEWER_USER_ID)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void navigationReturnsPlatformOnlyWhenTenantContextMissingForSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/me/navigation").header("Host", "admin.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].labelKey").value("nav.group.administration"))
				.andExpect(jsonPath("$.data.items[0].children.length()").value(6))
				.andExpect(jsonPath("$.data.items[0].children[0].labelKey").value("nav.platform_tenants"));
	}

	@Test
	void navigationReturnsFilteredItemsForViewer() throws Exception {
		mockMvc.perform(get("/api/v1/me/navigation").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].labelKey").value("nav.group.workspace"))
				.andExpect(jsonPath("$.data.items[0].children.length()").value(2))
				.andExpect(jsonPath("$.data.items[1].labelKey").value("nav.group.security"))
				.andExpect(jsonPath("$.data.items[1].children.length()").value(2));
	}

	@Test
	void navigationReturnsAllSeededRootsForAdmin() throws Exception {
		mockMvc.perform(get("/api/v1/me/navigation").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(3))
				.andExpect(jsonPath("$.data.items[0].labelKey").value("nav.group.workspace"))
				.andExpect(jsonPath("$.data.items[0].children.length()").value(4))
				.andExpect(jsonPath("$.data.items[1].labelKey").value("nav.group.security"))
				.andExpect(jsonPath("$.data.items[1].children.length()").value(2))
				.andExpect(jsonPath("$.data.items[2].labelKey").value("nav.group.administration"))
				.andExpect(jsonPath("$.data.items[2].children.length()").value(6));
	}

	@Test
	void navigationHidesItemsRequiringPlanFeatureUntilSubscriptionIncludesThem() throws Exception {
		var dash = navMenuItemRepository.findById(NAV_DASH_ID).orElseThrow();
		dash.setRequiredPlanFeatureCode("COMMERCIAL_BILLING");
		navMenuItemRepository.save(dash);

		mockMvc.perform(get("/api/v1/me/navigation").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].children.length()").value(3))
				.andExpect(jsonPath("$.data.items[1].children.length()").value(2))
				.andExpect(jsonPath("$.data.items[2].children.length()").value(6));

		UUID billingFeatureId = planFeatureRepository.findByCode("COMMERCIAL_BILLING").orElseThrow().getId();
		String createPlan = "{\"code\":\"m3_navfeat\",\"sortOrder\":1,\"active\":true,\"planFeatureIds\":[\"%s\"]}"
				.formatted(billingFeatureId);
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createPlan)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		String planId = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();
		String subBody = "{\"commercialPlanId\":\"" + planId + "\",\"status\":\"ACTIVE\"}";
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/subscription").contentType(MediaType.APPLICATION_JSON)
				.content(subBody).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/me/navigation").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].children.length()").value(4))
				.andExpect(jsonPath("$.data.items[1].children.length()").value(2))
				.andExpect(jsonPath("$.data.items[2].children.length()").value(6));
	}

	@Test
	void platformSettingsForbiddenForNonOperator() throws Exception {
		mockMvc.perform(get("/api/v1/platform/settings").with(user(VIEWER_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void platformSettingsOkForSeededPlatformSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/settings").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[*].key", hasItem("platform.product_name")))
				.andExpect(jsonPath("$.data.entries[*].key", hasItem("platform.application_name")));
	}

	@Test
	void platformSettingsRejectUnknownBillingKey() throws Exception {
		String body = "{\"entries\":[{\"key\":\"billing.stripe.unknown\",\"value\":\"1\"}]}";
		mockMvc.perform(patch("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void platformSettingsRejectUnknownNonBillingKey() throws Exception {
		String body = "{\"entries\":[{\"key\":\"platform.evil\",\"value\":\"x\"}]}";
		mockMvc.perform(patch("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void platformSettingsAcceptApplicationNamePatch() throws Exception {
		String body = "{\"entries\":[{\"key\":\"platform.application_name\",\"value\":\"Wage Payroll Dev\"}]}";
		mockMvc.perform(patch("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNoContent());
	}

	@Test
	void platformSettingsAcceptCustomDateFormatAndExposesItInMe() throws Exception {
		String body = "{\"entries\":[{\"key\":\"platform.date_format\",\"value\":\"dd-MM-yyyy\"}]}";
		mockMvc.perform(patch("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/me").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.dateFormat").value("dd-MM-yyyy"));
	}

	@Test
	void platformSettingsAcceptBillingStripeEnabledToggle() throws Exception {
		String body = "{\"entries\":[{\"key\":\"billing.stripe.enabled\",\"value\":\"1\"}]}";
		mockMvc.perform(patch("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNoContent());
	}

	@Test
	void platformMailTestForbiddenForNonOperator() throws Exception {
		mockMvc.perform(post("/api/v1/platform/settings/mail/test").contentType(MediaType.APPLICATION_JSON)
				.content("{\"to\":\"ops@example.com\"}").with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void platformMailTestReturns400WhenMailApiNotConfigured() throws Exception {
		String clearMail = "{\"entries\":["
				+ "{\"key\":\"mail.api.base_url\",\"value\":\"\"},"
				+ "{\"key\":\"mail.api.project_key\",\"value\":\"\"},"
				+ "{\"key\":\"mail.api.username\",\"value\":\"\"},"
				+ "{\"key\":\"mail.api.password\",\"value\":\"\"}]}";
		mockMvc.perform(patch("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content(clearMail)
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/platform/settings/mail/test").contentType(MediaType.APPLICATION_JSON)
				.content("{\"to\":\"ops@example.com\"}").with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}
}
