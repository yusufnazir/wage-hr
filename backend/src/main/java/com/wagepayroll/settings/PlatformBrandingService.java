package com.wagepayroll.settings;

import java.util.Set;

import com.wagepayroll.config.AppPublicProperties;
import com.wagepayroll.domain.setting.PlatformSettingRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Resolves non-secret display and link-building fields from {@code platform_setting} with property fallbacks (see
 * {@code docs/modules/platform-settings.md}).
 */
@Service
public class PlatformBrandingService {

	private static final String K_APPLICATION_NAME = "platform.application_name";
	private static final String K_PRODUCT_NAME = "platform.product_name";
	private static final String K_BASE_URL = "platform.base_url";
	private static final String K_DATE_FORMAT = "platform.date_format";

	private static final Set<String> DATE_FORMATS = Set.of("yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "ISO-8601");

	private final PlatformSettingRepository platformSettingRepository;
	private final AppPublicProperties appPublicProperties;
	private final String springApplicationName;

	public PlatformBrandingService(PlatformSettingRepository platformSettingRepository, AppPublicProperties appPublicProperties,
			@Value("${spring.application.name:wage-payroll-api}") String springApplicationName) {
		this.platformSettingRepository = platformSettingRepository;
		this.appPublicProperties = appPublicProperties;
		this.springApplicationName = springApplicationName;
	}

	@Transactional(readOnly = true)
	public String applicationName() {
		return resolveApplicationName();
	}

	@Transactional(readOnly = true)
	public String dateFormatToken() {
		return resolveDateFormat();
	}

	/**
	 * Canonical public base for clients and email links: {@code platform.base_url} when set, otherwise
	 * {@code app.public.frontend-origin}.
	 */
	@Transactional(readOnly = true)
	public String publicBaseUrl() {
		return resolvePublicBaseUrl();
	}

	/** One read transaction for {@code GET /api/v1/me} tenant payload fields. */
	@Transactional(readOnly = true)
	public TenantMeBranding tenantMeBranding() {
		return new TenantMeBranding(resolveApplicationName(), resolveDateFormat(), resolvePublicBaseUrl());
	}

	public record TenantMeBranding(String applicationName, String dateFormat, String publicBaseUrl) {
	}

	private String resolveApplicationName() {
		String app = textSettingOrBlank(K_APPLICATION_NAME);
		if (StringUtils.hasText(app)) {
			return app.trim();
		}
		String legacy = textSettingOrBlank(K_PRODUCT_NAME);
		if (StringUtils.hasText(legacy)) {
			return legacy.trim();
		}
		return humanizeSpringApplicationName(springApplicationName);
	}

	private String resolveDateFormat() {
		String v = textSettingOrBlank(K_DATE_FORMAT);
		if (PlatformIntegrationSettingsValidator.isAllowedDateFormat(v)) {
			return v.trim();
		}
		return "yyyy-MM-dd";
	}

	private String resolvePublicBaseUrl() {
		String fromDb = textSettingOrBlank(K_BASE_URL);
		if (StringUtils.hasText(fromDb)) {
			return stripTrailingSlashes(fromDb.trim());
		}
		return stripTrailingSlashes(appPublicProperties.getFrontendOrigin().trim());
	}

	private String textSettingOrBlank(String key) {
		return platformSettingRepository.findByKey(key).map(e -> e.getValueText()).orElse("");
	}

	private static String stripTrailingSlashes(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		String out = s;
		while (out.endsWith("/") && out.length() > 1) {
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}

	private static String humanizeSpringApplicationName(String raw) {
		if (raw == null || raw.isBlank()) {
			return "Wage Payroll";
		}
		String slug = raw.replace('-', ' ').replace('_', ' ').trim();
		if (slug.isEmpty()) {
			return "Wage Payroll";
		}
		return Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
	}
}
