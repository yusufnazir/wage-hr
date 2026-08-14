package com.wagepayroll.mail;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.wagepayroll.domain.mailtemplate.MailTemplateEntity;
import com.wagepayroll.domain.mailtemplate.MailTemplateLocaleEntity;
import com.wagepayroll.domain.mailtemplate.MailTemplateLocaleRepository;
import com.wagepayroll.domain.mailtemplate.MailTemplateRepository;

import org.springframework.stereotype.Service;

@Service
public class MailTemplateCatalogService {

	private static final Pattern TAG = Pattern.compile("<[^>]+>");

	private final MailTemplateRepository mailTemplateRepository;
	private final MailTemplateLocaleRepository mailTemplateLocaleRepository;

	public MailTemplateCatalogService(MailTemplateRepository mailTemplateRepository,
			MailTemplateLocaleRepository mailTemplateLocaleRepository) {
		this.mailTemplateRepository = mailTemplateRepository;
		this.mailTemplateLocaleRepository = mailTemplateLocaleRepository;
	}

	/**
	 * Renders {@link MailTemplateCodes#TENANT_INVITATION} when active and a supported locale row is available;
	 * otherwise empty (caller uses hard-coded fallback).
	 */
	public Optional<RenderedCatalogEmail> tryRenderTenantInvitation(String preferredLocaleTag, Map<String, String> vars) {
		return tryRenderActiveTemplate(MailTemplateCodes.TENANT_INVITATION, preferredLocaleTag, vars);
	}

	public Optional<RenderedCatalogEmail> tryRenderEmailVerification(String preferredLocaleTag, Map<String, String> vars) {
		return tryRenderActiveTemplate(MailTemplateCodes.EMAIL_VERIFICATION, preferredLocaleTag, vars);
	}

	public Optional<RenderedCatalogEmail> tryRenderPasswordResetRequest(String preferredLocaleTag, Map<String, String> vars) {
		return tryRenderActiveTemplate(MailTemplateCodes.PASSWORD_RESET_REQUEST, preferredLocaleTag, vars);
	}

	public Optional<RenderedCatalogEmail> tryRenderEmployeeAccountActivation(String preferredLocaleTag, Map<String, String> vars) {
		return tryRenderActiveTemplate(MailTemplateCodes.EMPLOYEE_ACCOUNT_ACTIVATION, preferredLocaleTag, vars);
	}

	public Optional<RenderedCatalogEmail> tryRenderEmployeeAccountLinked(String preferredLocaleTag, Map<String, String> vars) {
		return tryRenderActiveTemplate(MailTemplateCodes.EMPLOYEE_ACCOUNT_LINKED, preferredLocaleTag, vars);
	}

	private Optional<RenderedCatalogEmail> tryRenderActiveTemplate(String code, String preferredLocaleTag, Map<String, String> vars) {
		Optional<MailTemplateEntity> template = mailTemplateRepository.findByCodeAndActiveIsTrue(code);
		if (template.isEmpty()) {
			return Optional.empty();
		}
		List<MailTemplateLocaleEntity> rows = mailTemplateLocaleRepository.findByMailTemplateIdOrderByLocaleAsc(template.get().getId());
		MailTemplateLocaleEntity row = pickRow(preferredLocaleTag, rows);
		if (row == null) {
			return Optional.empty();
		}
		String subject = applyVars(row.getSubject(), vars);
		String html = applyVars(row.getBodyHtml(), vars);
		return Optional.of(new RenderedCatalogEmail(subject, html, htmlToPlain(html)));
	}

	private static MailTemplateLocaleEntity pickRow(String preferredLocaleTag, List<MailTemplateLocaleEntity> rows) {
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		String p = preferredLocaleTag == null ? "" : preferredLocaleTag.trim().toLowerCase();
		boolean wantNl = p.startsWith("nl");
		if (wantNl) {
			MailTemplateLocaleEntity nl = rows.stream().filter(r -> "nl".equals(r.getLocale())).findFirst().orElse(null);
			if (nl != null) {
				return nl;
			}
		}
		return rows.stream().filter(r -> "en".equals(r.getLocale())).findFirst().orElse(null);
	}

	private static String applyVars(String template, Map<String, String> vars) {
		String out = template;
		for (Map.Entry<String, String> e : vars.entrySet()) {
			String v = e.getValue() == null ? "" : e.getValue();
			out = out.replace("{{" + e.getKey() + "}}", v);
		}
		return out;
	}

	private static String htmlToPlain(String html) {
		String withBreaks = html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</p>", "\n\n");
		return TAG.matcher(withBreaks).replaceAll("").trim();
	}
}
