package com.wagepayroll.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Plain invitation tokens in API responses are allowed only when {@code app.invitation.expose-plain-token} is true
 * <em>and</em> an approved non-production profile is active ({@code dev}, {@code test}, or {@code local}).
 */
@Component
public class InvitationTokenExposure {

	private static final Logger log = LoggerFactory.getLogger(InvitationTokenExposure.class);

	private final Environment environment;
	private final InvitationProperties invitationProperties;

	public InvitationTokenExposure(Environment environment, InvitationProperties invitationProperties) {
		this.environment = environment;
		this.invitationProperties = invitationProperties;
	}

	public boolean effectiveExposePlainToken() {
		if (!invitationProperties.isExposePlainToken()) {
			return false;
		}
		for (String p : environment.getActiveProfiles()) {
			if ("dev".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p) || "local".equalsIgnoreCase(p)) {
				return true;
			}
		}
		return false;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void logExposurePolicy() {
		if (!invitationProperties.isExposePlainToken()) {
			return;
		}
		if (effectiveExposePlainToken()) {
			log.warn("app.invitation.expose-plain-token is enabled with a non-production profile: API may include devPlainToken on invitation create — never enable in production");
			return;
		}
		log.warn("app.invitation.expose-plain-token is set but IGNORED: active profiles do not include dev, test, or local — devPlainToken will never be returned");
	}
}
