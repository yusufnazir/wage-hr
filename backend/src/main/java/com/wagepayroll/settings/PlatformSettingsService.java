package com.wagepayroll.settings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wagepayroll.api.dto.SettingEntryDto;
import com.wagepayroll.api.dto.SettingsPatchRequest;
import com.wagepayroll.domain.setting.PlatformSettingEntity;
import com.wagepayroll.domain.setting.PlatformSettingRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformSettingsService {

	private final PlatformSettingRepository platformSettingRepository;

	public PlatformSettingsService(PlatformSettingRepository platformSettingRepository) {
		this.platformSettingRepository = platformSettingRepository;
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
