package com.wagepayroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.forwarding")
public class ForwardingProperties {

	/**
	 * When true, Spring uses forwarded headers for servlet request URL (align with reverse proxy).
	 */
	private boolean trustProxy = false;

	public boolean isTrustProxy() {
		return trustProxy;
	}

	public void setTrustProxy(boolean trustProxy) {
		this.trustProxy = trustProxy;
	}
}
