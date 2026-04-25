package com.wagepayroll.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.billing.paypal.webhook-id=")
class BillingPaypalWebhookNotConfiguredIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void paypalWebhookReturns503WhenWebhookIdUnset() throws Exception {
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON)
				.content("{\"event_type\":\"X\"}").header("PayPal-Transmission-Id", "tr_x")).andExpect(status().isServiceUnavailable());
	}
}
