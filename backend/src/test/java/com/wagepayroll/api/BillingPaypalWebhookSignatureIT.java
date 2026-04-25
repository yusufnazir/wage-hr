package com.wagepayroll.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

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
class BillingPaypalWebhookSignatureIT {

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
		registry.add("app.billing.paypal.webhook-id", () -> "test_paypal_webhook_id_1");
		registry.add("app.billing.paypal.verify-signature", () -> "true");
	}

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void resetPaypalMock() {
		PAYPAL_API.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				String path = request.getPath();
				if ("/v1/oauth2/token".equals(path)) {
					return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
							.setBody("{\"access_token\":\"mock_token\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
				}
				if ("/v1/notifications/verify-webhook-signature".equals(path)) {
					return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
							.setBody("{\"verification_status\":\"SUCCESS\"}");
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
	void paypalWebhookWithVerifyCallsPayPalApis() throws Exception {
		String body = "{\"id\":\"WH-PP-SIG-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"webhook_id\":\"test_paypal_webhook_id_1\"}";
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_sig_001").header("PayPal-Transmission-Time", "2020-01-01T00:00:00Z")
				.header("PayPal-Transmission-Sig", "sig").header("PayPal-Cert-Url", "https://api.sandbox.paypal.com/v1/notifications/certs/CERT-test")
				.header("PayPal-Auth-Algo", "SHA256withRSA")).andExpect(status().isOk()).andExpect(jsonPath("$.received").value(true))
				.andExpect(jsonPath("$.duplicate").value(false))
				.andExpect(jsonPath("$.tenantResolutionState").value("UNRESOLVED_INSUFFICIENT_DATA"));
	}

	@Test
	void paypalWebhookRejectsWhenPayPalReturnsFailure() throws Exception {
		PAYPAL_API.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				String path = request.getPath();
				if ("/v1/oauth2/token".equals(path)) {
					return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
							.setBody("{\"access_token\":\"mock_token\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
				}
				if ("/v1/notifications/verify-webhook-signature".equals(path)) {
					return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
							.setBody("{\"verification_status\":\"FAILURE\"}");
				}
				return new MockResponse().setResponseCode(404);
			}
		});
		String body = "{\"id\":\"WH-PP-SIG-2\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"webhook_id\":\"test_paypal_webhook_id_1\"}";
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_sig_002").header("PayPal-Transmission-Time", "2020-01-01T00:00:00Z")
				.header("PayPal-Transmission-Sig", "sig").header("PayPal-Cert-Url", "https://api.sandbox.paypal.com/v1/notifications/certs/CERT-test")
				.header("PayPal-Auth-Algo", "SHA256withRSA")).andExpect(status().isBadRequest());
	}
}
