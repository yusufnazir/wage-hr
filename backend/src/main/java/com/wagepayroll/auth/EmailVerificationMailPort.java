package com.wagepayroll.auth;

/**
 * Outbound email verification link delivery (same transport as password reset — see {@code OutboundMailService}).
 */
public interface EmailVerificationMailPort {

	void sendEmailVerificationLink(String email, String verifyUrl, String firstName, String tenantHandle,
			String preferredLocaleForEmail);
}
