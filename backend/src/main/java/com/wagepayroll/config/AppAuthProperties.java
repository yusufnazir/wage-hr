package com.wagepayroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {

	/**
	 * TTL for one-time email verification tokens (hours). See {@code docs/modules/account-registration.md}.
	 */
	private int emailVerificationTtlHours = 24;

	public int getEmailVerificationTtlHours() {
		return emailVerificationTtlHours;
	}

	public void setEmailVerificationTtlHours(int emailVerificationTtlHours) {
		this.emailVerificationTtlHours = emailVerificationTtlHours;
	}
}
