package com.wagepayroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fallback outbound mail API settings when {@code platform_setting} keys are unset (see
 * {@code docs/modules/platform-settings.md}).
 */
@ConfigurationProperties(prefix = "app.mail.api")
public class MailApiProperties {

	private String baseUrl = "";

	private String projectKey = "";

	private String username = "";

	private String password = "";

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean isFullyConfigured() {
		return hasText(baseUrl) && hasText(projectKey) && hasText(username) && hasText(password);
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}
}
