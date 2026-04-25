package com.wagepayroll.billing;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared redirect URL rules for Stripe Checkout / Portal and PayPal subscription flows.
 * When {@code allowInsecureRedirectUrls} is true, {@code http} is allowed for {@code localhost}, {@code 127.0.0.1},
 * and {@code *.lvh.me} hosts (local multi-tenant dev / Playwright, same convention as {@code Host} routing docs).
 */
@Component
public class BillingRedirectUrlPolicy {

	@Value("${app.billing.stripe.allow-insecure-checkout-urls:false}")
	private boolean allowInsecureRedirectUrls;

	public void validateTenantBillingRedirectUrl(String url) {
		if (!StringUtils.hasText(url) || url.length() > 2048) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_REDIRECT_URL_REQUIRED");
		}
		final URI uri;
		try {
			uri = URI.create(url.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_REDIRECT_URL_INVALID");
		}
		String scheme = uri.getScheme();
		if (!"https".equalsIgnoreCase(scheme)) {
			if (allowInsecureRedirectUrls && "http".equalsIgnoreCase(scheme)) {
				String host = uri.getHost();
				if (host != null && isInsecureHttpDevHost(host)) {
					return;
				}
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_REDIRECT_URL_HTTPS_REQUIRED");
		}
	}

	static boolean isInsecureHttpDevHost(String host) {
		if (host.equals("localhost") || host.equals("127.0.0.1")) {
			return true;
		}
		return host.equals("lvh.me") || host.endsWith(".lvh.me");
	}
}
