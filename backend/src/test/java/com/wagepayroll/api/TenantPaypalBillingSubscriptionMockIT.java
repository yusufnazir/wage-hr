package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.domain.plan.CommercialPlanEntity;
import com.wagepayroll.domain.plan.CommercialPlanRepository;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantPaypalBillingSubscriptionMockIT {

	static final MockWebServer PAYPAL_API = new MockWebServer();

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
	private PlatformSettingRepository platformSettingRepository;

	@Autowired
	private CommercialPlanRepository commercialPlanRepository;

	private String commercialPlanId;

	@BeforeEach
	void enablePaypalAndMockApis() {
		CommercialPlanEntity plan = new CommercialPlanEntity();
		plan.setId(UUID.randomUUID());
		plan.setCode("pp_sub_mock_" + Long.toHexString(System.nanoTime()));
		plan.setSortOrder(1);
		plan.setActive(true);
		Instant now = Instant.now();
		plan.setCreatedAt(now);
		plan.setUpdatedAt(now);
		commercialPlanId = commercialPlanRepository.save(plan).getId().toString();
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
	void subscriptionReturnsApprovalUrlFromPayPal() throws Exception {
		String body = "{\"commercialPlanId\":\"" + commercialPlanId
				+ "\",\"planId\":\"P-MOCKPLAN\",\"returnUrl\":\"http://localhost:3007/ok\",\"cancelUrl\":\"http://localhost:3007/cancel\"}";
		mockMvc.perform(post("/api/v1/tenant/billing/paypal/subscription").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user("30000000-0000-0000-0000-000000000001")).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalUrl").value("https://www.sandbox.paypal.com/checkoutnow?token=EC-MOCK"));
	}
}
