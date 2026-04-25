package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.plan.PlanFeatureRepository;
import com.wagepayroll.domain.setting.PlatformSettingEntity;
import com.wagepayroll.domain.setting.PlatformSettingRepository;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantPaypalBillingPlanMismatchIT {

	static final MockWebServer PAYPAL_API = new MockWebServer();

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	static {
		try {
			PAYPAL_API.start();
		}
		catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@DynamicPropertySource
	static void paypalApiBase(DynamicPropertyRegistry registry) {
		registry.add("app.billing.paypal.api-base", () -> "http://127.0.0.1:" + PAYPAL_API.getPort());
		registry.add("app.billing.paypal.client-id", () -> "fake_client");
		registry.add("app.billing.paypal.client-secret", () -> "fake_secret");
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PlatformSettingRepository platformSettingRepository;

	@Autowired
	private PlanFeatureRepository planFeatureRepository;

	@BeforeEach
	void enablePaypalAndStubSubscriptionCreate() {
		PlatformSettingEntity e = platformSettingRepository.findByKey("billing.paypal.enabled").orElseThrow();
		e.setValueText("1");
		platformSettingRepository.save(e);
		PAYPAL_API.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				String path = request.getPath();
				if ("/v1/oauth2/token".equals(path)) {
					return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
							.setBody("{\"access_token\":\"mock_token\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
				}
				if ("/v1/billing/subscriptions".equals(path)) {
					return new MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody("""
							{"id":"I-MOCKSUB","status":"APPROVAL_PENDING","links":[
							{"href":"https://www.sandbox.paypal.com/checkoutnow?token=EC-MOCK","rel":"approve","method":"GET"}
							]}
							""".replaceAll("\\s+", ""));
				}
				return new MockResponse().setResponseCode(404);
			}
		});
	}

	@AfterAll
	static void shutdown() throws IOException {
		PAYPAL_API.shutdown();
	}

	@Test
	void subscriptionReturns400WhenPaypalPlanIdDoesNotMatchCommercialPlanBinding() throws Exception {
		String expectedPlan = "P-EXPbind_" + Long.toHexString(System.nanoTime());
		String commercialPlanId = createCommercialPlanWithPaypalBinding(expectedPlan);
		String body = "{\"commercialPlanId\":\"" + commercialPlanId
				+ "\",\"planId\":\"P-WRONG_PLAN\",\"returnUrl\":\"http://localhost:3007/ok\",\"cancelUrl\":\"http://localhost:3007/cancel\"}";
		mockMvc.perform(post("/api/v1/tenant/billing/paypal/subscription").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void subscriptionSucceedsWhenPaypalPlanIdMatchesCommercialPlanBinding() throws Exception {
		String expectedPlan = "P-EXPbind_" + Long.toHexString(System.nanoTime());
		String commercialPlanId = createCommercialPlanWithPaypalBinding(expectedPlan);
		String body = "{\"commercialPlanId\":\"" + commercialPlanId + "\",\"planId\":\"" + expectedPlan
				+ "\",\"returnUrl\":\"http://localhost:3007/ok\",\"cancelUrl\":\"http://localhost:3007/cancel\"}";
		mockMvc.perform(post("/api/v1/tenant/billing/paypal/subscription").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalUrl").value("https://www.sandbox.paypal.com/checkoutnow?token=EC-MOCK"));
	}

	private String createCommercialPlanWithPaypalBinding(String paypalPlanId) throws Exception {
		UUID tenantCore = planFeatureRepository.findByCode("TENANT_CORE").orElseThrow().getId();
		String code = "pp_bind_" + Long.toHexString(System.nanoTime());
		String createJson = "{\"code\":\"%s\",\"sortOrder\":3,\"active\":true,\"planFeatureIds\":[\"%s\"],\"paypalBillingPlanId\":\"%s\"}"
				.formatted(code, tenantCore, paypalPlanId);
		MvcResult created = mockMvc
				.perform(post("/api/v1/platform/commercial-plans").contentType(MediaType.APPLICATION_JSON).content(createJson)
						.with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();
	}
}
