package com.wagepayroll.mail;

/**
 * Outbound mail abstraction — no bodies persisted to MariaDB (see {@code docs/modules/mail-adapter.md}).
 */
public interface MailSendPort {

	/**
	 * @return provider opaque id (stored on {@code notification.external_message_id} when invoked from notification pipeline).
	 */
	String sendInvitationEmail(InvitationEmailRequest request);
}
