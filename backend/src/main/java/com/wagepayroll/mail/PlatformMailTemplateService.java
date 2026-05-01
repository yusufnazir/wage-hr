package com.wagepayroll.mail;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.MailTemplateDetailDto;
import com.wagepayroll.api.dto.MailTemplateListItemDto;
import com.wagepayroll.api.dto.MailTemplateLocalePutDto;
import com.wagepayroll.api.dto.MailTemplateLocaleResponseDto;
import com.wagepayroll.api.dto.MailTemplatePutRequest;
import com.wagepayroll.domain.mailtemplate.MailTemplateEntity;
import com.wagepayroll.domain.mailtemplate.MailTemplateLocaleEntity;
import com.wagepayroll.domain.mailtemplate.MailTemplateLocaleRepository;
import com.wagepayroll.domain.mailtemplate.MailTemplateRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformMailTemplateService {

	private static final int SUBJECT_MAX = 500;
	private static final int BODY_HTML_MAX = 256_000;

	private final MailTemplateRepository mailTemplateRepository;
	private final MailTemplateLocaleRepository mailTemplateLocaleRepository;

	public PlatformMailTemplateService(MailTemplateRepository mailTemplateRepository,
			MailTemplateLocaleRepository mailTemplateLocaleRepository) {
		this.mailTemplateRepository = mailTemplateRepository;
		this.mailTemplateLocaleRepository = mailTemplateLocaleRepository;
	}

	public List<MailTemplateListItemDto> listAll() {
		return mailTemplateRepository.findAllByOrderByCodeAsc().stream()
				.map(t -> new MailTemplateListItemDto(t.getId(), t.getCode(), t.getContentVersion(), t.isActive(),
						DateTimeFormatter.ISO_INSTANT.format(t.getUpdatedAt())))
				.toList();
	}

	public MailTemplateDetailDto get(UUID id) {
		MailTemplateEntity t = mailTemplateRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MAIL_TEMPLATE_NOT_FOUND"));
		List<MailTemplateLocaleResponseDto> locales = mailTemplateLocaleRepository.findByMailTemplateIdOrderByLocaleAsc(id).stream()
				.map(l -> new MailTemplateLocaleResponseDto(l.getLocale(), l.getSubject(), l.getBodyHtml()))
				.toList();
		return new MailTemplateDetailDto(t.getId(), t.getCode(), t.getContentVersion(), t.isActive(),
				DateTimeFormatter.ISO_INSTANT.format(t.getUpdatedAt()), locales);
	}

	@Transactional
	public void replace(UUID id, MailTemplatePutRequest body) {
		if (body == null || body.ifUpdatedAt() == null || body.ifUpdatedAt().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_IF_UPDATED_AT_REQUIRED");
		}
		final Instant ifUpdatedAt;
		try {
			ifUpdatedAt = Instant.parse(body.ifUpdatedAt());
		}
		catch (DateTimeParseException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_IF_UPDATED_AT_INVALID");
		}
		MailTemplateEntity t = mailTemplateRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MAIL_TEMPLATE_NOT_FOUND"));
		if (!t.getUpdatedAt().truncatedTo(ChronoUnit.MILLIS).equals(ifUpdatedAt.truncatedTo(ChronoUnit.MILLIS))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "MAIL_TEMPLATE_CONFLICT");
		}
		validateLocales(body.locales());
		Instant now = Instant.now();
		mailTemplateLocaleRepository.deleteByMailTemplateId(id);
		mailTemplateLocaleRepository.flush();
		for (MailTemplateLocalePutDto loc : body.locales()) {
			MailTemplateLocaleEntity row = new MailTemplateLocaleEntity();
			row.setId(UUID.randomUUID());
			row.setMailTemplateId(id);
			row.setLocale(loc.locale().trim().toLowerCase());
			row.setSubject(loc.subject().trim());
			row.setBodyHtml(loc.bodyHtml().trim());
			row.setCreatedAt(now);
			row.setUpdatedAt(now);
			mailTemplateLocaleRepository.save(row);
		}
		String newVersion = nextContentVersion();
		t.setContentVersion(newVersion);
		t.setActive(body.active());
		t.setUpdatedAt(now);
		mailTemplateRepository.save(t);
	}

	private static void validateLocales(List<MailTemplateLocalePutDto> locales) {
		if (locales == null || locales.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_LOCALES_REQUIRED");
		}
		Set<String> seen = new HashSet<>();
		List<String> required = new ArrayList<>(List.of("en", "nl"));
		for (MailTemplateLocalePutDto loc : locales) {
			if (loc == null || loc.locale() == null || loc.subject() == null || loc.bodyHtml() == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_LOCALE_INVALID");
			}
			String locale = loc.locale().trim().toLowerCase();
			if (!locale.equals("en") && !locale.equals("nl")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_LOCALE_UNSUPPORTED");
			}
			if (!seen.add(locale)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_LOCALE_DUPLICATE");
			}
			String subject = loc.subject().trim();
			String body = loc.bodyHtml().trim();
			if (subject.isEmpty() || body.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_LOCALE_BLANK");
			}
			if (subject.length() > SUBJECT_MAX || body.length() > BODY_HTML_MAX) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_LOCALE_TOO_LARGE");
			}
			required.remove(locale);
		}
		if (!required.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_LOCALES_INCOMPLETE");
		}
	}

	private static String nextContentVersion() {
		String v = "mt-" + Long.toHexString(System.currentTimeMillis());
		return v.length() <= 32 ? v : v.substring(0, 32);
	}
}
