package com.wagepayroll.mail;

public interface EmployeeAccountMailPort {

	void sendActivationEmail(String email, String firstName, String companyName, String tenantHandle, String roleName,
			String activationUrl, String preferredLocale);

	void sendLinkedEmail(String email, String firstName, String companyName, String tenantHandle, String roleName,
			String preferredLocale);
}
