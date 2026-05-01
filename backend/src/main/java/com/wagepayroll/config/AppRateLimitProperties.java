package com.wagepayroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.rate-limit")
public class AppRateLimitProperties {

	private int loginMaxAttempts = 5;
	private int loginWindowMinutes = 15;
	private int forgotPasswordMaxAttempts = 5;
	private int forgotPasswordWindowMinutes = 60;
	private int passwordResetTokenTtlMinutes = 60;

	public int getLoginMaxAttempts() {
		return loginMaxAttempts;
	}

	public void setLoginMaxAttempts(int loginMaxAttempts) {
		this.loginMaxAttempts = loginMaxAttempts;
	}

	public int getLoginWindowMinutes() {
		return loginWindowMinutes;
	}

	public void setLoginWindowMinutes(int loginWindowMinutes) {
		this.loginWindowMinutes = loginWindowMinutes;
	}

	public int getForgotPasswordMaxAttempts() {
		return forgotPasswordMaxAttempts;
	}

	public void setForgotPasswordMaxAttempts(int forgotPasswordMaxAttempts) {
		this.forgotPasswordMaxAttempts = forgotPasswordMaxAttempts;
	}

	public int getForgotPasswordWindowMinutes() {
		return forgotPasswordWindowMinutes;
	}

	public void setForgotPasswordWindowMinutes(int forgotPasswordWindowMinutes) {
		this.forgotPasswordWindowMinutes = forgotPasswordWindowMinutes;
	}

	public int getPasswordResetTokenTtlMinutes() {
		return passwordResetTokenTtlMinutes;
	}

	public void setPasswordResetTokenTtlMinutes(int passwordResetTokenTtlMinutes) {
		this.passwordResetTokenTtlMinutes = passwordResetTokenTtlMinutes;
	}
}
