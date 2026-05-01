package com.wagepayroll.mail;

import java.util.UUID;

/** Ephemeral inputs for rendering + sending an invitation email (not persisted as a whole). */
public record InvitationEmailRequest(UUID tenantId, String tenantHandle, String invitedEmail, String plainToken,
		String preferredLocaleForEmail) {
}
