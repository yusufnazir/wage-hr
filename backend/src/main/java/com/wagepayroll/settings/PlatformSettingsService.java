package com.wagepayroll.settings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wagepayroll.api.dto.SettingEntryDto;
import com.wagepayroll.api.dto.SettingsPatchRequest;
import com.wagepayroll.billing.BillingIntegrationSettingsValidator;
import com.wagepayroll.domain.roletemplate.RoleTemplateRepository;
import com.wagepayroll.domain.setting.PlatformSettingEntity;
import com.wagepayroll.domain.setting.PlatformSettingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformSettingsService {

	private final PlatformSettingRepository platformSettingRepository;
	private final RoleTemplateRepository roleTemplateRepository;

	public PlatformSettingsService(PlatformSettingRepository platformSettingRepository,
			RoleTemplateRepository roleTemplateRepository) {
		this.platformSettingRepository = platformSettingRepository;
		this.roleTemplateRepository = roleTemplateRepository;
	}

	@Transactional(readOnly = true)
	public List<SettingEntryDto> list() {
		List<SettingEntryDto> out = new ArrayList<>();
		for (PlatformSettingEntity e : platformSettingRepository.findAllByOrderByKeyAsc()) {
			out.add(new SettingEntryDto(e.getKey(), e.getValueText()));
		}
		return out;
	}

	@Transactional
	public void patch(SettingsPatchRequest body) {
		if (body == null || body.entries() == null) {
			return;
		}
		Instant now = Instant.now();
		for (SettingEntryDto entry : body.entries()) {
			SettingsEntryValidator.validateKey(entry.key());
			SettingsEntryValidator.validateValue(entry.value());
			BillingIntegrationSettingsValidator.validateIfBillingKey(entry);
			PlatformIntegrationSettingsValidator.validateIfKnownScope(entry);
			if ("auth.registration.default_role_template_code".equals(entry.key())) {
				String v = entry.value() == null ? "" : entry.value().trim();
				if (v.isEmpty()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SETTINGS_VALUE");
				}
				roleTemplateRepository.findByCodeIgnoreCase(v)
						.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE_TEMPLATE_CODE"));
			}
			PlatformSettingEntity e = platformSettingRepository.findByKey(entry.key()).orElse(null);
			if (e == null) {
				PlatformSettingEntity n = new PlatformSettingEntity();
				n.setId(UUID.randomUUID());
				n.setKey(entry.key());
				n.setValueText(entry.value());
				n.setCreatedAt(now);
				n.setUpdatedAt(now);
				platformSettingRepository.save(n);
			}
			else {
				e.setValueText(entry.value());
				e.setUpdatedAt(now);
			}
		}
	}
}
