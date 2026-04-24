package com.wagepayroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public class AppCorsProperties {

	/**
	 * Comma-separated allowed origin patterns (Spring 6 patterns), e.g.
	 * http://auth.lvh.me:3007,http://*.lvh.me:3007
	 */
	private String allowedOriginPatterns = "http://localhost:3007,http://127.0.0.1:3007,http://auth.lvh.me:3007,http://app.lvh.me:3007,http://*.lvh.me:3007";

	public String getAllowedOriginPatterns() {
		return allowedOriginPatterns;
	}

	public void setAllowedOriginPatterns(String allowedOriginPatterns) {
		this.allowedOriginPatterns = allowedOriginPatterns;
	}
}
