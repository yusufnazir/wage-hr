package com.wagepayroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.invitation")
public class InvitationProperties {

	/**
	 * When true, the app may expose the raw invite token on create — only if active profiles include {@code dev},
	 * {@code test}, or {@code local} (see {@link InvitationTokenExposure}). Bind from env
	 * {@code APP_INVITATION_EXPOSE_PLAIN_TOKEN}.
	 */
	private boolean exposePlainToken = false;

	public boolean isExposePlainToken() {
		return exposePlainToken;
	}

	public void setExposePlainToken(boolean exposePlainToken) {
		this.exposePlainToken = exposePlainToken;
	}
}
