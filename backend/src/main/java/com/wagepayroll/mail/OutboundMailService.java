package com.wagepayroll.mail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.auth.PasswordResetMailPort;
import com.wagepayroll.settings.MailApiSettingsMergeService;
import com.wagepayroll.settings.MailApiSettingsMergeService.MergedMailApiSettings;
import com.wagepayroll.settings.PlatformBrandingService;
import com.wagepayroll.settings.PlatformUrlJoin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Single outbound mail path: resolves {@code mail.api.*} from DB then {@code app.mail.api.*} (see
 * {@code docs/modules/platform-settings.md}). When all four API fields are set, posts JSON to {@code {baseUrl}/send};
 * otherwise logs (local/dev parity with the former logging adapters).
 */
@Component
@Primary
public class OutboundMailService implements MailSendPort, PasswordResetMailPort {

	private static final Logger log = LoggerFactory.getLogger(OutboundMailService.class);

	private final MailApiSettingsMergeService mailApiSettingsMergeService;
	private final PlatformBrandingService platformBrandingService;

	public OutboundMailService(MailApiSettingsMergeService mailApiSettingsMergeService,
			PlatformBrandingService platformBrandingService) {
		this.mailApiSettingsMergeService = mailApiSettingsMergeService;
		this.platformBrandingService = platformBrandingService;
	}

	@Override
	public String sendInvitationEmail(InvitationEmailRequest request) {
		MergedMailApiSettings mail = mailApiSettingsMergeService.resolve();
		String base = platformBrandingService.publicBaseUrl();
		String inviteLink = PlatformUrlJoin.joinPublicBaseAndPath(base, "/register?inviteToken=" + request.plainToken());
		String subject = "Invitation: " + request.tenantHandle();
		String text = "You have been invited to join tenant \"" + request.tenantHandle() + "\".\nOpen: " + inviteLink + "\n";
		if (!mail.isFullyConfigured()) {
			log.debug("mail.invitation (log-only) to={} tenant={} linkPresent=true", maskEmail(request.invitedEmail()), request.tenantHandle());
			return "log-" + UUID.randomUUID();
		}
		return postTransactional(mail, request.invitedEmail(), subject, text, "invitation");
	}

	@Override
	public void sendPasswordResetLink(String email, String resetUrl) {
		MergedMailApiSettings mail = mailApiSettingsMergeService.resolve();
		String subject = "Password reset";
		String text = "Reset your password using this link (expires soon):\n" + resetUrl + "\n";
		if (!mail.isFullyConfigured()) {
			log.info("[password-reset] email={} resetUrl={}", email, resetUrl);
			return;
		}
		postTransactional(mail, email, subject, text, "password_reset");
	}

	private String postTransactional(MergedMailApiSettings mail, String to, String subject, String text, String kind) {
		String base = trimTrailingSlash(mail.baseUrl());
		String url = base + "/send";
		String basic = Base64.getEncoder().encodeToString((mail.username() + ":" + mail.password()).getBytes(StandardCharsets.UTF_8));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("to", to);
		body.put("subject", subject);
		body.put("text", text);
		body.put("kind", kind);
		try {
			RestClient.create().post().uri(url).header(HttpHeaders.AUTHORIZATION, "Basic " + basic).header("X-Project-Key", mail.projectKey())
					.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
			return "http-" + UUID.randomUUID();
		}
		catch (RestClientException ex) {
			log.warn("Outbound mail HTTP failed kind={} to={}: {}", kind, maskEmail(to), ex.getMessage());
			return "http-failed-" + UUID.randomUUID();
		}
	}

	private static String trimTrailingSlash(String s) {
		String out = s.trim();
		while (out.endsWith("/") && out.length() > 1) {
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}

	private static String maskEmail(String email) {
		int at = email.indexOf('@');
		if (at <= 1) {
			return "*";
		}
		return email.charAt(0) + "***@" + email.substring(at + 1);
	}
}
