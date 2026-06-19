package com.wagepayroll.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.wagepayroll.domain.mailtemplate.MailTemplateEntity;
import com.wagepayroll.domain.mailtemplate.MailTemplateLocaleEntity;
import com.wagepayroll.domain.mailtemplate.MailTemplateLocaleRepository;
import com.wagepayroll.domain.mailtemplate.MailTemplateRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailTemplateCatalogServiceTest {

	@Mock
	private MailTemplateRepository mailTemplateRepository;

	@Mock
	private MailTemplateLocaleRepository mailTemplateLocaleRepository;

	private MailTemplateCatalogService service;

	@BeforeEach
	void setUp() {
		service = new MailTemplateCatalogService(mailTemplateRepository, mailTemplateLocaleRepository);
	}

	@Test
	void emailVerificationNlBeVariantResolvesNlRow() {
		UUID templateId = UUID.randomUUID();
		stubActiveTemplate(MailTemplateCodes.EMAIL_VERIFICATION, templateId);
		when(mailTemplateLocaleRepository.findByMailTemplateIdOrderByLocaleAsc(templateId)).thenReturn(List.of(
				locale(templateId, "en", "Verify {{firstName}}", "<p><a href=\"{{verifyLink}}\">Go</a></p>"),
				locale(templateId, "nl", "Verifieer {{firstName}}", "<p><a href=\"{{verifyLink}}\">Ga</a></p>")));

		Optional<RenderedCatalogEmail> rendered = service.tryRenderEmailVerification("nl-be",
				Map.of("firstName", "Ada", "verifyLink", "https://verify", "tenantHandle", "demo"));

		assertThat(rendered).isPresent();
		assertThat(rendered.get().subject()).isEqualTo("Verifieer Ada");
		assertThat(rendered.get().htmlBody()).contains("https://verify");
	}

	@Test
	void emailVerificationFallsBackToEnWhenNlMissing() {
		UUID templateId = UUID.randomUUID();
		stubActiveTemplate(MailTemplateCodes.EMAIL_VERIFICATION, templateId);
		when(mailTemplateLocaleRepository.findByMailTemplateIdOrderByLocaleAsc(templateId))
				.thenReturn(List.of(locale(templateId, "en", "Verify {{firstName}}", "<p>EN {{verifyLink}}</p>")));

		Optional<RenderedCatalogEmail> rendered = service.tryRenderEmailVerification("nl",
				Map.of("firstName", "Ada", "verifyLink", "https://verify", "tenantHandle", "demo"));

		assertThat(rendered).isPresent();
		assertThat(rendered.get().subject()).isEqualTo("Verify Ada");
		assertThat(rendered.get().htmlBody()).contains("EN https://verify");
	}

	@Test
	void emailVerificationReturnsEmptyWhenLocalesMissing() {
		UUID templateId = UUID.randomUUID();
		stubActiveTemplate(MailTemplateCodes.EMAIL_VERIFICATION, templateId);
		when(mailTemplateLocaleRepository.findByMailTemplateIdOrderByLocaleAsc(templateId)).thenReturn(List.of());

		Optional<RenderedCatalogEmail> rendered = service.tryRenderEmailVerification("nl",
				Map.of("firstName", "Ada", "verifyLink", "https://verify", "tenantHandle", "demo"));

		assertThat(rendered).isEmpty();
	}

	@Test
	void emailVerificationReturnsEmptyWhenTemplateInactive() {
		when(mailTemplateRepository.findByCodeAndActiveIsTrue(MailTemplateCodes.EMAIL_VERIFICATION)).thenReturn(Optional.empty());

		Optional<RenderedCatalogEmail> rendered = service.tryRenderEmailVerification("nl",
				Map.of("firstName", "Ada", "verifyLink", "https://verify", "tenantHandle", "demo"));

		assertThat(rendered).isEmpty();
	}

	@Test
	void passwordResetNlBeVariantResolvesNlRow() {
		UUID templateId = UUID.randomUUID();
		stubActiveTemplate(MailTemplateCodes.PASSWORD_RESET_REQUEST, templateId);
		when(mailTemplateLocaleRepository.findByMailTemplateIdOrderByLocaleAsc(templateId)).thenReturn(List.of(
				locale(templateId, "en", "Reset {{firstName}}", "<p>{{resetLink}} {{expiryMinutes}}</p>"),
				locale(templateId, "nl", "Herstel {{firstName}}", "<p>{{resetLink}} {{expiryMinutes}}</p>")));

		Optional<RenderedCatalogEmail> rendered = service.tryRenderPasswordResetRequest("nl-be",
				Map.of("firstName", "Lin", "resetLink", "https://reset", "expiryMinutes", "60"));

		assertThat(rendered).isPresent();
		assertThat(rendered.get().subject()).isEqualTo("Herstel Lin");
		assertThat(rendered.get().htmlBody()).contains("https://reset", "60");
	}

	@Test
	void passwordResetFallsBackToEnWhenNlMissing() {
		UUID templateId = UUID.randomUUID();
		stubActiveTemplate(MailTemplateCodes.PASSWORD_RESET_REQUEST, templateId);
		when(mailTemplateLocaleRepository.findByMailTemplateIdOrderByLocaleAsc(templateId))
				.thenReturn(List.of(locale(templateId, "en", "Reset {{firstName}}", "<p>{{resetLink}} {{expiryMinutes}}</p>")));

		Optional<RenderedCatalogEmail> rendered = service.tryRenderPasswordResetRequest("nl",
				Map.of("firstName", "Lin", "resetLink", "https://reset", "expiryMinutes", "45"));

		assertThat(rendered).isPresent();
		assertThat(rendered.get().subject()).isEqualTo("Reset Lin");
		assertThat(rendered.get().htmlBody()).contains("https://reset", "45");
	}

	@Test
	void passwordResetReturnsEmptyWhenLocalesMissing() {
		UUID templateId = UUID.randomUUID();
		stubActiveTemplate(MailTemplateCodes.PASSWORD_RESET_REQUEST, templateId);
		when(mailTemplateLocaleRepository.findByMailTemplateIdOrderByLocaleAsc(templateId)).thenReturn(List.of());

		Optional<RenderedCatalogEmail> rendered = service.tryRenderPasswordResetRequest("nl",
				Map.of("firstName", "Lin", "resetLink", "https://reset", "expiryMinutes", "45"));

		assertThat(rendered).isEmpty();
	}

	@Test
	void passwordResetReturnsEmptyWhenTemplateInactive() {
		when(mailTemplateRepository.findByCodeAndActiveIsTrue(MailTemplateCodes.PASSWORD_RESET_REQUEST))
				.thenReturn(Optional.empty());

		Optional<RenderedCatalogEmail> rendered = service.tryRenderPasswordResetRequest("nl",
				Map.of("firstName", "Lin", "resetLink", "https://reset", "expiryMinutes", "45"));

		assertThat(rendered).isEmpty();
	}

	private void stubActiveTemplate(String code, UUID templateId) {
		MailTemplateEntity template = new MailTemplateEntity();
		template.setId(templateId);
		template.setCode(code);
		template.setActive(true);
		template.setContentVersion("mt-seed-1");
		template.setCreatedAt(Instant.now());
		template.setUpdatedAt(Instant.now());
		when(mailTemplateRepository.findByCodeAndActiveIsTrue(code)).thenReturn(Optional.of(template));
	}

	private static MailTemplateLocaleEntity locale(UUID templateId, String locale, String subject, String bodyHtml) {
		MailTemplateLocaleEntity row = new MailTemplateLocaleEntity();
		row.setId(UUID.randomUUID());
		row.setMailTemplateId(templateId);
		row.setLocale(locale);
		row.setSubject(subject);
		row.setBodyHtml(bodyHtml);
		row.setCreatedAt(Instant.now());
		row.setUpdatedAt(Instant.now());
		return row;
	}
}
