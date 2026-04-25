package com.wagepayroll.billing;

import java.util.UUID;

import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import com.wagepayroll.domain.billing.BillingProviderLinkEntity;
import com.wagepayroll.domain.billing.BillingProviderLinkRepository;
import com.wagepayroll.domain.plan.CommercialPlanEntity;
import com.wagepayroll.domain.plan.CommercialPlanRepository;
import com.wagepayroll.domain.setting.PlatformSettingEntity;
import com.wagepayroll.domain.setting.PlatformSettingRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingStripeHostedCheckoutService {

	private final PlatformSettingRepository platformSettingRepository;
	private final BillingProviderLinkRepository billingProviderLinkRepository;
	private final CommercialPlanRepository commercialPlanRepository;
	private final BillingRedirectUrlPolicy billingRedirectUrlPolicy;

	@Value("${app.billing.stripe.secret-key:}")
	private String stripeSecretKey;

	public BillingStripeHostedCheckoutService(PlatformSettingRepository platformSettingRepository,
			BillingProviderLinkRepository billingProviderLinkRepository, CommercialPlanRepository commercialPlanRepository,
			BillingRedirectUrlPolicy billingRedirectUrlPolicy) {
		this.platformSettingRepository = platformSettingRepository;
		this.billingProviderLinkRepository = billingProviderLinkRepository;
		this.commercialPlanRepository = commercialPlanRepository;
		this.billingRedirectUrlPolicy = billingRedirectUrlPolicy;
	}

	public String createSubscriptionCheckoutSession(UUID tenantId, UUID commercialPlanId, String priceId, String successUrl,
			String cancelUrl) {
		requireStripeOperational();
		validatePriceId(priceId);
		billingRedirectUrlPolicy.validateTenantBillingRedirectUrl(successUrl);
		billingRedirectUrlPolicy.validateTenantBillingRedirectUrl(cancelUrl);
		String customerId = requireStripeCustomerId(tenantId);
		CommercialPlanEntity plan = commercialPlanRepository.findById(commercialPlanId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UNKNOWN_COMMERCIAL_PLAN"));
		if (!plan.isActive()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INACTIVE_COMMERCIAL_PLAN_FOR_CHECKOUT");
		}
		if (!StringUtils.hasText(plan.getStripeSubscriptionPriceId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMMERCIAL_PLAN_STRIPE_PRICE_NOT_CONFIGURED");
		}
		if (!plan.getStripeSubscriptionPriceId().trim().equals(priceId.trim())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STRIPE_PRICE_PLAN_MISMATCH");
		}
		RequestOptions opts = RequestOptions.builder().setApiKey(stripeSecretKey).build();
		try {
			SessionCreateParams params = SessionCreateParams.builder().setMode(SessionCreateParams.Mode.SUBSCRIPTION)
					.setCustomer(customerId).setClientReferenceId(tenantId.toString())
					.putMetadata("commercial_plan_id", commercialPlanId.toString())
					.setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
							.putMetadata("commercial_plan_id", commercialPlanId.toString()).build())
					.addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
					.setSuccessUrl(successUrl).setCancelUrl(cancelUrl).build();
			com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(params, opts);
			if (!StringUtils.hasText(session.getUrl())) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "STRIPE_CHECKOUT_NO_URL");
			}
			return session.getUrl();
		}
		catch (StripeException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "STRIPE_CHECKOUT_FAILED");
		}
	}

	public String createBillingPortalSession(UUID tenantId, String returnUrl) {
		requireStripeOperational();
		billingRedirectUrlPolicy.validateTenantBillingRedirectUrl(returnUrl);
		String customerId = requireStripeCustomerId(tenantId);
		RequestOptions opts = RequestOptions.builder().setApiKey(stripeSecretKey).build();
		try {
			com.stripe.param.billingportal.SessionCreateParams params = com.stripe.param.billingportal.SessionCreateParams.builder()
					.setCustomer(customerId).setReturnUrl(returnUrl).build();
			com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params, opts);
			if (!StringUtils.hasText(session.getUrl())) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "STRIPE_PORTAL_NO_URL");
			}
			return session.getUrl();
		}
		catch (StripeException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "STRIPE_PORTAL_FAILED");
		}
	}

	private void requireStripeOperational() {
		if (!isStripeBillingEnabledOnPlatform()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "BILLING_STRIPE_DISABLED");
		}
		if (!StringUtils.hasText(stripeSecretKey)) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STRIPE_SECRET_NOT_CONFIGURED");
		}
	}

	private boolean isStripeBillingEnabledOnPlatform() {
		return platformSettingRepository.findByKey(BillingTenantSummaryService.PLATFORM_KEY_STRIPE_ENABLED)
				.map(PlatformSettingEntity::getValueText)
				.map(v -> "1".equals(v.trim()))
				.orElse(false);
	}

	private String requireStripeCustomerId(UUID tenantId) {
		return billingProviderLinkRepository.findByTenantIdAndProvider(tenantId, BillingProvider.STRIPE.code())
				.map(BillingProviderLinkEntity::getExternalCustomerId)
				.filter(StringUtils::hasText)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BILLING_STRIPE_CUSTOMER_NOT_LINKED"));
	}

	private static void validatePriceId(String priceId) {
		if (!StringUtils.hasText(priceId) || priceId.length() > 255) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STRIPE_PRICE_ID_REQUIRED");
		}
		String t = priceId.trim();
		if (!t.startsWith("price_")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STRIPE_PRICE_ID_INVALID");
		}
	}

}
