package com.wagepayroll.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wagepayroll.domain.ledger.PlatformLedgerTemplateEntity;
import com.wagepayroll.domain.ledger.PlatformLedgerTemplateLocaleEntity;
import com.wagepayroll.domain.ledger.PlatformLedgerTemplateLocaleRepository;
import com.wagepayroll.domain.ledger.PlatformLedgerTemplateRepository;
import com.wagepayroll.domain.ledger.TenantLedgerEntity;
import com.wagepayroll.domain.ledger.TenantLedgerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerTemplateCopyService {

	private final PlatformLedgerTemplateRepository platformLedgerTemplateRepository;
	private final PlatformLedgerTemplateLocaleRepository platformLedgerTemplateLocaleRepository;
	private final TenantLedgerRepository tenantLedgerRepository;

	public LedgerTemplateCopyService(PlatformLedgerTemplateRepository platformLedgerTemplateRepository,
			PlatformLedgerTemplateLocaleRepository platformLedgerTemplateLocaleRepository,
			TenantLedgerRepository tenantLedgerRepository) {
		this.platformLedgerTemplateRepository = platformLedgerTemplateRepository;
		this.platformLedgerTemplateLocaleRepository = platformLedgerTemplateLocaleRepository;
		this.tenantLedgerRepository = tenantLedgerRepository;
	}

	@Transactional
	public void copyForCompany(UUID tenantId, UUID companyId, String payrollCountry) {
		if (tenantId == null || companyId == null || payrollCountry == null) {
			return;
		}
		String cc = payrollCountry.trim().toUpperCase(Locale.ROOT);
		List<PlatformLedgerTemplateEntity> sources = platformLedgerTemplateRepository
				.findByCountryCodeAndActiveIsTrueOrderByCodeAsc(cc);
		if (sources.isEmpty()) {
			return;
		}
		List<UUID> templateIds = sources.stream().map(PlatformLedgerTemplateEntity::getId).toList();
		List<PlatformLedgerTemplateLocaleEntity> localeRows = platformLedgerTemplateLocaleRepository
				.findByPlatformLedgerTemplateIdIn(templateIds);
		Map<UUID, String> englishDescriptionByTemplateId = localeRows.stream()
				.filter(r -> "en".equalsIgnoreCase(r.getLocale()))
				.collect(Collectors.toMap(PlatformLedgerTemplateLocaleEntity::getPlatformLedgerTemplateId,
						PlatformLedgerTemplateLocaleEntity::getDescription, (a, b) -> a));
		Instant now = Instant.now();
		for (PlatformLedgerTemplateEntity p : sources) {
			TenantLedgerEntity t = new TenantLedgerEntity();
			t.setId(UUID.randomUUID());
			t.setTenantId(tenantId);
			t.setCompanyId(companyId);
			t.setPlatformLedgerTemplateId(p.getId());
			t.setCode(p.getCode());
			t.setDescription(englishDescriptionByTemplateId.getOrDefault(p.getId(), ""));
			t.setActive(p.isActive());
			t.setCreatedAt(now);
			t.setUpdatedAt(now);
			tenantLedgerRepository.save(t);
		}
	}
}
