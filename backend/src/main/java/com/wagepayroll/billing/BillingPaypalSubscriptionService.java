package com.wagepayroll.billing;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.wagepayroll.domain.plan.CommercialPlanEntity;
import com.wagepayroll.domain.plan.CommercialPlanRepository;
import com.wagepayroll.domain.setting.PlatformSettingEntity;
import com.wagepayroll.domain.setting.PlatformSettingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * PayPal Billing Subscriptions v1: create subscription in {@code APPROVAL_PENDING} state and return the {@code approve} link.
 */
@Service
public class BillingPaypalSubscriptionService {

	private final PlatformSettingRepository platformSettingRepository;
	private final CommercialPlanRepository commercialPlanRepository;
	private final PayPalOAuthClient payPalOAuthClient;
	private final BillingRedirectUrlPolicy billingRedirectUrlPolicy;
	private final ObjectMapper objectMapper;
	private final RestClient restClient = RestClient.create();

	public BillingPaypalSubscriptionService(PlatformSettingRepository platformSettingRepository,
			CommercialPlanRepository commercialPlanRepository, PayPalOAuthClient payPalOAuthClient,
			BillingRedirectUrlPolicy billingRedirectUrlPolicy, ObjectMapper objectMapper) {
		this.platformSettingRepository = platformSettingRepository;
		this.commercialPlanRepository = commercialPlanRepository;
		this.payPalOAuthClient = payPalOAuthClient;
		this.billingRedirectUrlPolicy = billingRedirectUrlPolicy;
		this.objectMapper = objectMapper;
	}

	public String createSubscriptionApprovalUrl(UUID tenantId, String planId, String returnUrl, String cancelUrl, UUID commercialPlanId) {
		requirePaypalBillingEnabled();
		validatePlanId(planId);
		CommercialPlanEntity commercialPlan = commercialPlanRepository.findById(commercialPlanId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UNKNOWN_COMMERCIAL_PLAN"));
		if (!commercialPlan.isActive()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INACTIVE_COMMERCIAL_PLAN");
		}
		if (StringUtils.hasText(commercialPlan.getPaypalBillingPlanId())
				&& !commercialPlan.getPaypalBillingPlanId().trim().equals(planId.trim())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYPAL_PLAN_ID_MISMATCH");
		}
		billingRedirectUrlPolicy.validateTenantBillingRedirectUrl(returnUrl);
		billingRedirectUrlPolicy.validateTenantBillingRedirectUrl(cancelUrl);
		String customId = PaypalSubscriptionCustomId.encode(tenantId, commercialPlanId);
		if (customId.length() > 127) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYPAL_CUSTOM_ID_TOO_LONG");
		}
		String token = payPalOAuthClient.accessToken();
		String base = payPalOAuthClient.normalizedApiBase();
		ObjectNode body = objectMapper.createObjectNode();
		body.put("plan_id", planId.trim());
		body.put("custom_id", customId);
		ObjectNode app = body.putObject("application_context");
		app.put("return_url", returnUrl.trim());
		app.put("cancel_url", cancelUrl.trim());
		String createUrl = base + "/v1/billing/subscriptions";
		try {
			String raw = restClient.post().uri(createUrl).header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
			JsonNode root = objectMapper.readTree(raw);
			for (JsonNode link : root.path("links")) {
				if ("approve".equals(link.path("rel").asText())) {
					String href = link.path("href").asText(null);
					if (StringUtils.hasText(href)) {
						return href;
					}
				}
			}
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PAYPAL_SUBSCRIPTION_NO_APPROVE_LINK");
		}
		catch (ResponseStatusException ex) {
			throw ex;
		}
		catch (RestClientResponseException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PAYPAL_SUBSCRIPTION_HTTP_ERROR");
		}
		catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PAYPAL_SUBSCRIPTION_FAILED");
		}
	}

	private void requirePaypalBillingEnabled() {
		boolean on = platformSettingRepository.findByKey(BillingTenantSummaryService.PLATFORM_KEY_PAYPAL_ENABLED)
				.map(PlatformSettingEntity::getValueText)
				.map(v -> "1".equals(v.trim()))
				.orElse(false);
		if (!on) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "BILLING_PAYPAL_DISABLED");
		}
	}

	private static void validatePlanId(String planId) {
		if (!StringUtils.hasText(planId) || planId.length() > 128) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYPAL_PLAN_ID_REQUIRED");
		}
		String t = planId.trim();
		if (!t.startsWith("P-")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYPAL_PLAN_ID_INVALID");
		}
	}
}
