package com.wagepayroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.headers")
public class AppSecurityHeadersProperties {

	private String contentSecurityPolicy = "default-src 'none'; frame-ancestors 'none'";
	private String referrerPolicy = "strict-origin-when-cross-origin";
	private String permissionsPolicy = "geolocation=(), microphone=(), camera=()";

	public String getContentSecurityPolicy() {
		return contentSecurityPolicy;
	}

	public void setContentSecurityPolicy(String contentSecurityPolicy) {
		this.contentSecurityPolicy = contentSecurityPolicy;
	}

	public String getReferrerPolicy() {
		return referrerPolicy;
	}

	public void setReferrerPolicy(String referrerPolicy) {
		this.referrerPolicy = referrerPolicy;
	}

	public String getPermissionsPolicy() {
		return permissionsPolicy;
	}

	public void setPermissionsPolicy(String permissionsPolicy) {
		this.permissionsPolicy = permissionsPolicy;
	}
}
