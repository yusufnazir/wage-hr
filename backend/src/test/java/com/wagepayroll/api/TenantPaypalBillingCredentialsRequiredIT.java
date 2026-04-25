package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.domain.plan.CommercialPlanEntity;
import com.wagepayroll.domain.plan.CommercialPlanRepository;
import com.wagepayroll.domain.setting.PlatformSettingEntity;
import com.wagepayroll.domain.setting.PlatformSettingRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = { "app.billing.paypal.client-id=", "app.billing.paypal.client-secret=" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantPaypalBillingCredentialsRequiredIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlatformSettingRepository platformSettingRepository;

	@Autowired
	private CommercialPlanRepository commercialPlanRepository;

	private String commercialPlanId;

	@BeforeEach
	void enablePaypalBillingOnPlatform() {
		PlatformSettingEntity e = platformSettingRepository.findByKey("billing.paypal.enabled").orElseThrow();
		e.setValueText("1");
		platformSettingRepository.save(e);
		CommercialPlanEntity plan = new CommercialPlanEntity();
		plan.setId(UUID.randomUUID());
		plan.setCode("pp_cred_it_" + Long.toHexString(System.nanoTime()));
		plan.setSortOrder(1);
		plan.setActive(true);
		Instant now = Instant.now();
		plan.setCreatedAt(now);
		plan.setUpdatedAt(now);
		commercialPlanId = commercialPlanRepository.save(plan).getId().toString();
	}

	@Test
	void subscriptionReturns503WhenPaypalCredentialsMissing() throws Exception {
		String body = "{\"commercialPlanId\":\"" + commercialPlanId
				+ "\",\"planId\":\"P-TESTPLAN\",\"returnUrl\":\"http://localhost:3007/ok\",\"cancelUrl\":\"http://localhost:3007/cancel\"}";
		mockMvc.perform(post("/api/v1/tenant/billing/paypal/subscription").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isServiceUnavailable());
	}
}
