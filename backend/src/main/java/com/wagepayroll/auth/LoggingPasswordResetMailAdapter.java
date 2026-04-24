package com.wagepayroll.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class LoggingPasswordResetMailAdapter implements PasswordResetMailPort {

	private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailAdapter.class);

	@Override
	public void sendPasswordResetLink(String email, String resetUrl) {
		log.info("[password-reset] email={} resetUrl={}", email, resetUrl);
	}
}
