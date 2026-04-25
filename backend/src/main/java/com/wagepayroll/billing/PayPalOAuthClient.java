package com.wagepayroll.billing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * PayPal REST client-credentials OAuth with an in-memory token cache. Used by webhook signature verification and
 * tenant-facing billing APIs.
 */
@Component
public class PayPalOAuthClient {

	private final ObjectMapper objectMapper;
	private final RestClient restClient = RestClient.create();

	@Value("${app.billing.paypal.api-base:https://api-m.sandbox.paypal.com}")
	private String apiBase;

	@Value("${app.billing.paypal.client-id:}")
	private String clientId;

	@Value("${app.billing.paypal.client-secret:}")
	private String clientSecret;

	private volatile String cachedAccessToken;
	private volatile long cachedAccessTokenExpiresAtEpochSecond;

	public PayPalOAuthClient(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void requireCredentials() {
		if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PAYPAL_CREDENTIALS_REQUIRED");
		}
	}

	public String normalizedApiBase() {
		return normalizeApiBase(apiBase);
	}

	/** Returns a bearer token, refreshing when near expiry. Missing client id/secret → {@code 503 PAYPAL_CREDENTIALS_REQUIRED}. */
	public String accessToken() {
		requireCredentials();
		String base = normalizedApiBase();
		long now = Instant.now().getEpochSecond();
		String cached = cachedAccessToken;
		long exp = cachedAccessTokenExpiresAtEpochSecond;
		if (cached != null && now < exp - 60) {
			return cached;
		}
		synchronized (this) {
			now = Instant.now().getEpochSecond();
			cached = cachedAccessToken;
			exp = cachedAccessTokenExpiresAtEpochSecond;
			if (cached != null && now < exp - 60) {
				return cached;
			}
			String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
			String tokenUrl = base + "/v1/oauth2/token";
			try {
				String resp = restClient.post().uri(tokenUrl).header("Authorization", "Basic " + basic)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED).body("grant_type=client_credentials").retrieve()
						.body(String.class);
				JsonNode root = objectMapper.readTree(resp);
				String access = root.path("access_token").asText(null);
				if (!StringUtils.hasText(access)) {
					throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PAYPAL_OAUTH_INVALID_RESPONSE");
				}
				int expiresIn = root.path("expires_in").asInt(3600);
				cachedAccessToken = access;
				cachedAccessTokenExpiresAtEpochSecond = Instant.now().getEpochSecond() + Math.max(60, expiresIn);
				return access;
			}
			catch (ResponseStatusException ex) {
				throw ex;
			}
			catch (RestClientResponseException ex) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PAYPAL_OAUTH_HTTP_ERROR");
			}
			catch (Exception ex) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PAYPAL_OAUTH_FAILED");
			}
		}
	}

	static String normalizeApiBase(String raw) {
		if (raw == null || raw.isBlank()) {
			return "https://api-m.sandbox.paypal.com";
		}
		String t = raw.trim();
		while (t.endsWith("/")) {
			t = t.substring(0, t.length() - 1);
		}
		return t;
	}
}
