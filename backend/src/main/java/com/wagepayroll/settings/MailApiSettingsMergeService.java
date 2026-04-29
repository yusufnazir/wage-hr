package com.wagepayroll.settings;

import com.wagepayroll.config.MailApiProperties;
import com.wagepayroll.domain.setting.PlatformSettingRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MailApiSettingsMergeService {

	private static final String K_BASE = "mail.api.base_url";
	private static final String K_PROJECT = "mail.api.project_key";
	private static final String K_USER = "mail.api.username";
	private static final String K_PASS = "mail.api.password";

	private final PlatformSettingRepository platformSettingRepository;
	private final MailApiProperties mailApiProperties;

	public MailApiSettingsMergeService(PlatformSettingRepository platformSettingRepository, MailApiProperties mailApiProperties) {
		this.platformSettingRepository = platformSettingRepository;
		this.mailApiProperties = mailApiProperties;
	}

	@Transactional(readOnly = true)
	public MergedMailApiSettings resolve() {
		String base = coalesce(K_BASE, mailApiProperties.getBaseUrl());
		String project = coalesce(K_PROJECT, mailApiProperties.getProjectKey());
		String user = coalesce(K_USER, mailApiProperties.getUsername());
		String pass = coalesce(K_PASS, mailApiProperties.getPassword());
		return new MergedMailApiSettings(base, project, user, pass);
	}

	private String coalesce(String key, String propertyFallback) {
		return platformSettingRepository.findByKey(key).map(e -> e.getValueText()).filter(StringUtils::hasText).map(String::trim)
				.orElseGet(() -> StringUtils.hasText(propertyFallback) ? propertyFallback.trim() : "");
	}

	public record MergedMailApiSettings(String baseUrl, String projectKey, String username, String password) {

		public boolean isFullyConfigured() {
			return StringUtils.hasText(baseUrl) && StringUtils.hasText(projectKey) && StringUtils.hasText(username)
					&& StringUtils.hasText(password);
		}
	}
}
