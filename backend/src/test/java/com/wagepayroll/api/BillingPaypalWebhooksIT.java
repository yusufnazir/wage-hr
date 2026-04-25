package com.wagepayroll.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wagepayroll.billing.BillingWebhookProvider;
import com.wagepayroll.billing.PayPalTenantResolverV1;
import com.wagepayroll.domain.billing.BillingWebhookReceiptRepository;
import com.wagepayroll.domain.billing.TenantResolutionState;

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
class BillingPaypalWebhooksIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String DEMO_TENANT_ID = "10000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private BillingWebhookReceiptRepository billingWebhookReceiptRepository;

	@Test
	void paypalWebhookAcceptsTransmissionIdAndIdempotentReplay() throws Exception {
		String body = "{\"id\":\"WH-PP-IT-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"webhook_id\":\"test_paypal_webhook_id_1\"}";
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_it_001")).andExpect(status().isOk())
				.andExpect(jsonPath("$.received").value(true)).andExpect(jsonPath("$.duplicate").value(false))
				.andExpect(jsonPath("$.tenantResolutionState").value("UNRESOLVED_INSUFFICIENT_DATA"))
				.andExpect(jsonPath("$.tenantResolutionReasonCode").value("resource_missing"))
				.andExpect(jsonPath("$.tenantResolutionMissingFieldPath").value("resource"))
				.andExpect(jsonPath("$.tenantResolutionResolverVersion").value(PayPalTenantResolverV1.VERSION));

		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_it_001")).andExpect(status().isOk())
				.andExpect(jsonPath("$.duplicate").value(true))
				.andExpect(jsonPath("$.tenantResolutionState").value("UNRESOLVED_INSUFFICIENT_DATA"));

		var row = billingWebhookReceiptRepository
				.findByProviderAndProviderEventId(BillingWebhookProvider.PAYPAL, "tr_pp_it_001").orElseThrow();
		assertEquals(body, row.getRawPayload());
		assertEquals("PAYMENT.CAPTURE.COMPLETED", row.getEventType());
		assertEquals(TenantResolutionState.UNRESOLVED_INSUFFICIENT_DATA, row.getTenantResolutionState());
		assertEquals("resource_missing", row.getTenantResolutionReasonCode());
		assertEquals("resource", row.getTenantResolutionMissingFieldPath());
		assertEquals(PayPalTenantResolverV1.VERSION, row.getTenantResolutionResolverVersion());
	}

	@Test
	void paypalWebhookRejectsMismatchedWebhookId() throws Exception {
		String body = "{\"id\":\"WH-PP-IT-2\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"webhook_id\":\"wrong_id\"}";
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_it_002")).andExpect(status().isBadRequest());
	}

	@Test
	void paypalWebhookTenantResolutionResolvedWhenPayerLinked() throws Exception {
		String payerId = "PAYER_MAP_PP_WEBHOOK_IT";
		String putBody = "{\"externalCustomerId\":\"" + payerId + "\"}";
		mockMvc.perform(put("/api/v1/platform/tenants/" + DEMO_TENANT_ID + "/billing-provider-links/paypal")
				.contentType(MediaType.APPLICATION_JSON).content(putBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk());

		String body = "{\"id\":\"WH-PP-IT-3\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"webhook_id\":\"test_paypal_webhook_id_1\",\"resource\":{\"payer\":{\"payer_id\":\"%s\"}}}"
				.formatted(payerId);
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_it_003")).andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantResolutionState").value("RESOLVED")).andExpect(jsonPath("$.duplicate").value(false))
				.andExpect(jsonPath("$.tenantResolutionResolverVersion").value(PayPalTenantResolverV1.VERSION));

		var row = billingWebhookReceiptRepository
				.findByProviderAndProviderEventId(BillingWebhookProvider.PAYPAL, "tr_pp_it_003").orElseThrow();
		assertEquals(TenantResolutionState.RESOLVED, row.getTenantResolutionState());
		assertEquals(body, row.getRawPayload());
	}

	@Test
	void paypalWebhookTenantResolutionNoMatchWhenPayerUnknown() throws Exception {
		String body = "{\"id\":\"WH-PP-IT-4\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"webhook_id\":\"test_paypal_webhook_id_1\",\"resource\":{\"payer\":{\"payer_id\":\"PAYER_UNKNOWN_PP_IT\"}}}";
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_it_004")).andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantResolutionState").value("UNRESOLVED_NO_MATCH"))
				.andExpect(jsonPath("$.tenantResolutionReasonCode").value("billing_provider_link_not_found"));

		var row = billingWebhookReceiptRepository
				.findByProviderAndProviderEventId(BillingWebhookProvider.PAYPAL, "tr_pp_it_004").orElseThrow();
		assertEquals(TenantResolutionState.UNRESOLVED_NO_MATCH, row.getTenantResolutionState());
		assertEquals("billing_provider_link_not_found", row.getTenantResolutionReasonCode());
	}

	@Test
	void paypalWebhookTenantResolutionInsufficientWhenResourceWithoutPayer() throws Exception {
		String body = "{\"id\":\"WH-PP-IT-5\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"webhook_id\":\"test_paypal_webhook_id_1\",\"resource\":{\"status\":\"COMPLETED\"}}";
		mockMvc.perform(post("/api/v1/billing/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("PayPal-Transmission-Id", "tr_pp_it_005")).andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantResolutionState").value("UNRESOLVED_INSUFFICIENT_DATA"))
				.andExpect(jsonPath("$.tenantResolutionReasonCode").value("payer_missing"))
				.andExpect(jsonPath("$.tenantResolutionMissingFieldPath").value("resource.payer"));

		var row = billingWebhookReceiptRepository
				.findByProviderAndProviderEventId(BillingWebhookProvider.PAYPAL, "tr_pp_it_005").orElseThrow();
		assertEquals(TenantResolutionState.UNRESOLVED_INSUFFICIENT_DATA, row.getTenantResolutionState());
		assertEquals("payer_missing", row.getTenantResolutionReasonCode());
	}
}
