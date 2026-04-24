package com.wagepayroll.mail;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Default M2 adapter: logs only and returns a synthetic provider id (no external HTTP).
 * Replace with a real HTTP provider in production environments.
 */
@Component
@Primary
public class LoggingMailSendPort implements MailSendPort {

	private static final Logger log = LoggerFactory.getLogger(LoggingMailSendPort.class);

	@Override
	public String sendInvitationEmail(InvitationEmailRequest request) {
		String syntheticId = "log-" + UUID.randomUUID();
		log.debug("mail.invitation to={} tenant={} syntheticMessageId={} (token omitted from logs)",
				maskEmail(request.invitedEmail()), request.tenantHandle(), syntheticId);
		return syntheticId;
	}

	private static String maskEmail(String email) {
		int at = email.indexOf('@');
		if (at <= 1) {
			return "*";
		}
		return email.charAt(0) + "***@" + email.substring(at + 1);
	}
}
