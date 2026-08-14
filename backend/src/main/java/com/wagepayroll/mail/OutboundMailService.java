package com.wagepayroll.mail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.wagepayroll.auth.EmailVerificationMailPort;
import com.wagepayroll.auth.PasswordResetMailPort;
import com.wagepayroll.settings.MailApiSettingsMergeService;
import com.wagepayroll.settings.MailApiSettingsMergeService.MergedMailApiSettings;
import com.wagepayroll.settings.PlatformBrandingService;
import com.wagepayroll.settings.PlatformUrlJoin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single outbound mail path: resolves {@code mail.api.*} from DB then {@code app.mail.api.*} (see
 * {@code docs/modules/platform-settings.md}). When all four API fields are set, posts JSON to
 * {@code {baseUrl}/rest/v1/api/register-mail}; otherwise logs (local/dev parity with the former logging adapters).
 */
@Component
public class OutboundMailService implements MailSendPort, PasswordResetMailPort, EmailVerificationMailPort,
		EmployeeAccountMailPort {

	private static final Logger log = LoggerFactory.getLogger(OutboundMailService.class);

	private final MailApiSettingsMergeService mailApiSettingsMergeService;
	private final PlatformBrandingService platformBrandingService;
	private final MailTemplateCatalogService mailTemplateCatalogService;

	public OutboundMailService(MailApiSettingsMergeService mailApiSettingsMergeService,
			PlatformBrandingService platformBrandingService, MailTemplateCatalogService mailTemplateCatalogService) {
		this.mailApiSettingsMergeService = mailApiSettingsMergeService;
		this.platformBrandingService = platformBrandingService;
		this.mailTemplateCatalogService = mailTemplateCatalogService;
	}

	@Override
	public String sendInvitationEmail(InvitationEmailRequest request) {
		MergedMailApiSettings mail = mailApiSettingsMergeService.resolve();
		String base = platformBrandingService.publicBaseUrl();
		String inviteLink = PlatformUrlJoin.joinPublicBaseAndPath(base, "/register?inviteToken=" + request.plainToken());
		Map<String, String> vars = Map.of("tenantHandle", request.tenantHandle(), "inviteLink", inviteLink);
		Optional<RenderedCatalogEmail> catalog = mailTemplateCatalogService.tryRenderTenantInvitation(request.preferredLocaleForEmail(), vars);
		String subject;
		String text;
		String html = null;
		if (catalog.isPresent()) {
			RenderedCatalogEmail r = catalog.get();
			subject = r.subject();
			text = r.textBody();
			html = r.htmlBody();
		}
		else {
			subject = "Invitation: " + request.tenantHandle();
			text = "You have been invited to join tenant \"" + request.tenantHandle() + "\".\nOpen: " + inviteLink + "\n";
		}
		if (!mail.isFullyConfigured()) {
			log.debug("mail.invitation (log-only) to={} tenant={} linkPresent=true", maskEmail(request.invitedEmail()), request.tenantHandle());
			return "log-" + UUID.randomUUID();
		}
		return postTransactional(mail, request.invitedEmail(), subject, text, html, "invitation");
	}

	@Override
	public void sendEmailVerificationLink(String email, String verifyUrl, String firstName, String tenantHandle,
			String preferredLocaleForEmail) {
		MergedMailApiSettings mail = mailApiSettingsMergeService.resolve();
		Map<String, String> vars = new LinkedHashMap<>();
		vars.put("firstName", safeTemplateValue(firstName));
		vars.put("verifyLink", safeTemplateValue(verifyUrl));
		vars.put("tenantHandle", safeTemplateValue(tenantHandle));
		Optional<RenderedCatalogEmail> catalog = mailTemplateCatalogService.tryRenderEmailVerification(preferredLocaleForEmail, vars);
		String subject;
		String text;
		String html = null;
		if (catalog.isPresent()) {
			RenderedCatalogEmail r = catalog.get();
			subject = r.subject();
			text = r.textBody();
			html = r.htmlBody();
		}
		else {
			subject = "Verify your email";
			text = "Confirm your email address using this link (expires in 24 hours):\n" + verifyUrl + "\n";
		}
		if (!mail.isFullyConfigured()) {
			log.info("[email-verification] email={} verifyUrl={}", email, verifyUrl);
			return;
		}
		postTransactional(mail, email, subject, text, html, "email_verification");
	}

	@Override
	public void sendPasswordResetLink(String email, String resetUrl, String firstName, String preferredLocaleForEmail,
			String expiryMinutes) {
		MergedMailApiSettings mail = mailApiSettingsMergeService.resolve();
		Map<String, String> vars = new LinkedHashMap<>();
		vars.put("firstName", safeTemplateValue(firstName));
		vars.put("resetLink", safeTemplateValue(resetUrl));
		vars.put("expiryMinutes", safeTemplateValue(expiryMinutes));
		Optional<RenderedCatalogEmail> catalog = mailTemplateCatalogService.tryRenderPasswordResetRequest(preferredLocaleForEmail, vars);
		String subject;
		String text;
		String html = null;
		if (catalog.isPresent()) {
			RenderedCatalogEmail r = catalog.get();
			subject = r.subject();
			text = r.textBody();
			html = r.htmlBody();
		}
		else {
			subject = "Password reset";
			text = "Reset your password using this link (expires soon):\n" + resetUrl + "\n";
		}
		if (!mail.isFullyConfigured()) {
			log.info("[password-reset] email={} resetUrl={}", email, resetUrl);
			return;
		}
		postTransactional(mail, email, subject, text, html, "password_reset");
	}

	@Override
	public void sendActivationEmail(String email, String firstName, String companyName, String tenantHandle,
			String roleName, String activationUrl, String preferredLocale) {
		sendEmployeeAccountMail(MailTemplateCodes.EMPLOYEE_ACCOUNT_ACTIVATION, email, firstName, companyName, tenantHandle,
				roleName, activationUrl, preferredLocale, "employee_account_activation");
	}

	@Override
	public void sendLinkedEmail(String email, String firstName, String companyName, String tenantHandle, String roleName,
			String preferredLocale) {
		sendEmployeeAccountMail(MailTemplateCodes.EMPLOYEE_ACCOUNT_LINKED, email, firstName, companyName, tenantHandle,
				roleName, null, preferredLocale, "employee_account_linked");
	}

	private void sendEmployeeAccountMail(String templateCode, String email, String firstName, String companyName,
			String tenantHandle, String roleName, String activationUrl, String preferredLocale, String logKind) {
		MergedMailApiSettings mail = mailApiSettingsMergeService.resolve();
		Map<String, String> vars = new LinkedHashMap<>();
		vars.put("firstName", safeTemplateValue(firstName));
		vars.put("companyName", safeTemplateValue(companyName));
		vars.put("tenantHandle", safeTemplateValue(tenantHandle));
		vars.put("roleName", safeTemplateValue(roleName));
		vars.put("activationLink", safeTemplateValue(activationUrl));
		Optional<RenderedCatalogEmail> catalog = MailTemplateCodes.EMPLOYEE_ACCOUNT_ACTIVATION.equals(templateCode)
				? mailTemplateCatalogService.tryRenderEmployeeAccountActivation(preferredLocale, vars)
				: mailTemplateCatalogService.tryRenderEmployeeAccountLinked(preferredLocale, vars);
		String subject;
		String text;
		String html = null;
		if (catalog.isPresent()) {
			RenderedCatalogEmail r = catalog.get();
			subject = r.subject();
			text = r.textBody();
			html = r.htmlBody();
		}
		else if (MailTemplateCodes.EMPLOYEE_ACCOUNT_ACTIVATION.equals(templateCode)) {
			subject = "Your account for " + safeTemplateValue(companyName);
			text = "An account has been created for you at " + companyName + " with role " + roleName + ".\nActivate: "
					+ activationUrl + "\n";
		}
		else {
			subject = "Linked to " + safeTemplateValue(companyName);
			text = "Your existing account has been linked to " + companyName + " with role " + roleName
					+ ".\nSign in with your existing password.\n";
		}
		if (!mail.isFullyConfigured()) {
			log.info("[{}] email={} activationUrl={}", logKind, email, activationUrl);
			return;
		}
		postTransactional(mail, email, subject, text, html, logKind);
	}

	/**
	 * Sends a one-off operator-triggered test email from platform settings using the same outbound integration.
	 */
	public void sendPlatformSettingsTestMail(String to) {
		MergedMailApiSettings mail = mailApiSettingsMergeService.resolve();
		if (!mail.isFullyConfigured()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAIL_API_NOT_CONFIGURED");
		}
		String id = postTransactional(mail, to, "Wage Payroll mail API test",
				"This is a test email sent from Platform settings.",
				"<p>This is a <strong>test email</strong> sent from Platform settings.</p>", "settings_test_mail");
		if (id.startsWith("http-failed-")) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MAIL_API_TEST_FAILED");
		}
	}

	private static String safeTemplateValue(String value) {
		return value == null ? "" : value;
	}

	private String postTransactional(MergedMailApiSettings mail, String to, String subject, String text, String html, String kind) {
		String base = trimTrailingSlash(mail.baseUrl());
		String url = base + "/rest/v1/api/register-mail";
		String basic = Base64.getEncoder().encodeToString((mail.username() + ":" + mail.password()).getBytes(StandardCharsets.UTF_8));
		String payloadText = html != null && !html.isBlank() ? html : text;
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("projectId", mail.projectKey());
		body.put("subject", subject);
		body.put("text", payloadText);
		body.put("to", java.util.List.of(to));
		try {
			RestClient.create().post().uri(url).header(HttpHeaders.AUTHORIZATION, "Basic " + basic).contentType(MediaType.APPLICATION_JSON)
					.body(body).retrieve().body(String.class);
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
