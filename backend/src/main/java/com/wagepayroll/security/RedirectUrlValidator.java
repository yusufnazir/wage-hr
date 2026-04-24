package com.wagepayroll.security;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.wagepayroll.config.AppHostProperties;

/**
 * Validates returnTo / post-login redirects: only {@code *.BASE_DOMAIN} on http (local) or https.
 */
@Component
public class RedirectUrlValidator {

	private final AppHostProperties hostProperties;

	public RedirectUrlValidator(AppHostProperties hostProperties) {
		this.hostProperties = hostProperties;
	}

	public boolean isAllowed(String returnTo, boolean allowLocalHttp) {
		if (!StringUtils.hasText(returnTo)) {
			return false;
		}
		String t = returnTo.trim();
		if (t.contains("\r") || t.contains("\n")) {
			return false;
		}
		if (t.startsWith("/") && !t.startsWith("//")) {
			return true;
		}
		URI uri;
		try {
			uri = URI.create(t);
		}
		catch (IllegalArgumentException e) {
			return false;
		}
		if (uri.getUserInfo() != null) {
			return false;
		}
		String scheme = uri.getScheme();
		if (scheme == null) {
			return false;
		}
		if ("https".equalsIgnoreCase(scheme)) {
			return hostAllowed(uri.getHost());
		}
		if ("http".equalsIgnoreCase(scheme) && allowLocalHttp) {
			return hostAllowed(uri.getHost());
		}
		return false;
	}

	private boolean hostAllowed(String host) {
		if (host == null) {
			return false;
		}
		String h = host.toLowerCase(Locale.ROOT);
		String base = hostProperties.getBaseDomain().trim().toLowerCase(Locale.ROOT);
		return h.equals(base) || h.endsWith("." + base);
	}

	public boolean isAllowedForLocalDev(String returnTo) {
		return isAllowed(returnTo, true);
	}

	/**
	 * Reserved subdomains that may appear in redirects (auth, app, api, tenant hosts).
	 */
	public Set<String> allowedSubdomainsForDocs() {
		return Set.of(hostProperties.getAuthSubdomain(), hostProperties.getAppSubdomain(), "api", "demo");
	}
}
