package com.wagepayroll.auth;

/**
 * Outbound password-reset link delivery. Production: HTTP mail API; local: log adapter.
 */
public interface PasswordResetMailPort {

	void sendPasswordResetLink(String email, String resetUrl, String firstName, String preferredLocaleForEmail,
			String expiryMinutes);
}
