package com.wagepayroll.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "app.billing.paypal.verify-signature=true", "app.billing.paypal.client-id=",
		"app.billing.paypal.client-secret=" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingPaypalWebhookCredentialsRequiredIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void paypalWebhookReturns503WhenVerifyEnabledButCredentialsMissing() throws Exception {
		String body = "{\"id\":\"WH-PP-503\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"webhook_id\":\"test_paypal_webhook_id_1\"}";
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_503").header("PayPal-Transmission-Time", "2020-01-01T00:00:00Z")
				.header("PayPal-Transmission-Sig", "sig").header("PayPal-Cert-Url", "https://example.invalid/cert.pem")
				.header("PayPal-Auth-Algo", "SHA256withRSA")).andExpect(status().isServiceUnavailable());
	}
}
