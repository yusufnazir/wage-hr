package com.wagepayroll.settings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wagepayroll.api.dto.SettingEntryDto;
import com.wagepayroll.api.dto.SettingsPatchRequest;
import com.wagepayroll.domain.setting.TenantSettingEntity;
import com.wagepayroll.domain.setting.TenantSettingRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSettingsService {

	private final TenantSettingRepository tenantSettingRepository;

	public TenantSettingsService(TenantSettingRepository tenantSettingRepository) {
		this.tenantSettingRepository = tenantSettingRepository;
	}

	@Transactional(readOnly = true)
	public List<SettingEntryDto> list(UUID tenantId) {
		List<SettingEntryDto> out = new ArrayList<>();
		for (TenantSettingEntity e : tenantSettingRepository.findByTenantIdOrderByKeyAsc(tenantId)) {
			out.add(new SettingEntryDto(e.getKey(), e.getValueText()));
		}
		return out;
	}

	@Transactional
	public void patch(UUID tenantId, SettingsPatchRequest body) {
		if (body == null || body.entries() == null) {
			return;
		}
		Instant now = Instant.now();
		for (SettingEntryDto entry : body.entries()) {
			SettingsEntryValidator.validateKey(entry.key());
			SettingsEntryValidator.validateValue(entry.value());
			TenantSettingEntity e = tenantSettingRepository.findByTenantIdAndKey(tenantId, entry.key()).orElse(null);
			if (e == null) {
				TenantSettingEntity n = new TenantSettingEntity();
				n.setId(UUID.randomUUID());
				n.setTenantId(tenantId);
				n.setKey(entry.key());
				n.setValueText(entry.value());
				n.setCreatedAt(now);
				n.setUpdatedAt(now);
				tenantSettingRepository.save(n);
			}
			else {
				e.setValueText(entry.value());
				e.setUpdatedAt(now);
			}
		}
	}
}
