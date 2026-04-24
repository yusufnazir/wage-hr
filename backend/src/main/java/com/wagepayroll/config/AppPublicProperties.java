package com.wagepayroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser-facing URLs for server-generated links (password reset, etc.).
 */
@ConfigurationProperties(prefix = "app.public")
public class AppPublicProperties {

	/**
	 * Origin for reset link (scheme + host + port), e.g. {@code http://auth.lvh.me:3007}.
	 */
	private String frontendOrigin = "http://auth.lvh.me:3007";

	public String getFrontendOrigin() {
		return frontendOrigin;
	}

	public void setFrontendOrigin(String frontendOrigin) {
		this.frontendOrigin = frontendOrigin;
	}
}
