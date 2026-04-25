package com.wagepayroll.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.wagepayroll.domain.billing.BillingUsageEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantBillingUsageEventsIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final UUID DEMO_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private BillingUsageEventRepository billingUsageEventRepository;

	@Test
	void usageEventAcceptsAndIdempotentReplay() throws Exception {
		String body = "{\"metricKey\":\"PAYROLL_RUN\",\"quantity\":1,\"idempotencyKey\":\"idem-usage-001\"}";
		mockMvc.perform(post("/api/v1/tenant/billing/usage-events").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.received").value(true))
				.andExpect(jsonPath("$.data.duplicate").value(false));

		mockMvc.perform(post("/api/v1/tenant/billing/usage-events").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.received").value(true))
				.andExpect(jsonPath("$.data.duplicate").value(true));

		assertEquals(1, billingUsageEventRepository.countByTenantId(DEMO_TENANT_ID));
	}

	@Test
	void usageEventRejectsUnknownMetric() throws Exception {
		String body = "{\"metricKey\":\"UNKNOWN_METRIC\",\"quantity\":1,\"idempotencyKey\":\"idem-usage-002\"}";
		mockMvc.perform(post("/api/v1/tenant/billing/usage-events").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest());
	}
}
