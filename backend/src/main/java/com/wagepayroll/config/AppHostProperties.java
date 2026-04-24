package com.wagepayroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.host")
public class AppHostProperties {

	private String baseDomain = "lvh.me";
	private String authSubdomain = "auth";
	private String appSubdomain = "app";
	/**
	 * Comma-separated extra reserved subdomains (lowercase).
	 */
	private String reservedSubdomainsExtra = "";
	private String cookieDomain = ".lvh.me";

	public String getBaseDomain() {
		return baseDomain;
	}

	public void setBaseDomain(String baseDomain) {
		this.baseDomain = baseDomain;
	}

	public String getAuthSubdomain() {
		return authSubdomain;
	}

	public void setAuthSubdomain(String authSubdomain) {
		this.authSubdomain = authSubdomain;
	}

	public String getAppSubdomain() {
		return appSubdomain;
	}

	public void setAppSubdomain(String appSubdomain) {
		this.appSubdomain = appSubdomain;
	}

	public String getReservedSubdomainsExtra() {
		return reservedSubdomainsExtra;
	}

	public void setReservedSubdomainsExtra(String reservedSubdomainsExtra) {
		this.reservedSubdomainsExtra = reservedSubdomainsExtra;
	}

	public String getCookieDomain() {
		return cookieDomain;
	}

	public void setCookieDomain(String cookieDomain) {
		this.cookieDomain = cookieDomain;
	}
}
