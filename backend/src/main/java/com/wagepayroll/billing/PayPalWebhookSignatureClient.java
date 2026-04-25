package com.wagepayroll.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * PayPal REST: {@code POST /v1/notifications/verify-webhook-signature} using bearer token from {@link PayPalOAuthClient}.
 */
@Component
public class PayPalWebhookSignatureClient {

	private final ObjectMapper objectMapper;
	private final PayPalOAuthClient payPalOAuthClient;
	private final RestClient restClient = RestClient.create();

	public PayPalWebhookSignatureClient(ObjectMapper objectMapper, PayPalOAuthClient payPalOAuthClient) {
		this.objectMapper = objectMapper;
		this.payPalOAuthClient = payPalOAuthClient;
	}

	public void verifySignatureOrThrow(PayPalWebhookIngress ingress, String configuredWebhookId, JsonNode webhookEvent) {
		if (!StringUtils.hasText(ingress.transmissionTime()) || !StringUtils.hasText(ingress.transmissionSig())
				|| !StringUtils.hasText(ingress.certUrl()) || !StringUtils.hasText(ingress.authAlgo())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_PAYPAL_WEBHOOK_HEADERS");
		}
		String base = payPalOAuthClient.normalizedApiBase();
		String token = payPalOAuthClient.accessToken();
		ObjectNode body = objectMapper.createObjectNode();
		body.put("auth_algo", ingress.authAlgo());
		body.put("cert_url", ingress.certUrl());
		body.put("transmission_id", ingress.transmissionId());
		body.put("transmission_sig", ingress.transmissionSig());
		body.put("transmission_time", ingress.transmissionTime());
		body.put("webhook_id", configuredWebhookId);
		body.set("webhook_event", webhookEvent);
		String verifyUrl = base + "/v1/notifications/verify-webhook-signature";
		try {
			String raw = restClient.post().uri(verifyUrl).header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
			JsonNode root = objectMapper.readTree(raw);
			String status = root.path("verification_status").asText("");
			if (!"SUCCESS".equals(status)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAYPAL_WEBHOOK_SIGNATURE");
			}
		}
		catch (ResponseStatusException ex) {
			throw ex;
		}
		catch (RestClientResponseException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PAYPAL_VERIFY_HTTP_ERROR");
		}
		catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PAYPAL_VERIFY_FAILED");
		}
	}
}
