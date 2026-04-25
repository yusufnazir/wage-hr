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
@TestPropertySource(properties = "app.billing.stripe.webhook-secret=")
class BillingStripeWebhookNotConfiguredIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void stripeWebhookReturns503WhenSecretUnset() throws Exception {
		mockMvc.perform(post("/api/v1/billing/webhooks/stripe").contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":\"evt_x\",\"object\":\"event\"}").header("Stripe-Signature", "t=1,v1=ab"))
				.andExpect(status().isServiceUnavailable());
	}
}
