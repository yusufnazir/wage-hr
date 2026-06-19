package com.wagepayroll.banktemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.wagepayroll.domain.banktemplate.PlatformBankTemplateEntity;
import com.wagepayroll.domain.banktemplate.PlatformBankTemplateRepository;
import com.wagepayroll.domain.banktemplate.TenantBankTemplateEntity;
import com.wagepayroll.domain.banktemplate.TenantBankTemplateRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankTemplateCopyService {

	private final PlatformBankTemplateRepository platformBankTemplateRepository;
	private final TenantBankTemplateRepository tenantBankTemplateRepository;

	public BankTemplateCopyService(PlatformBankTemplateRepository platformBankTemplateRepository,
			TenantBankTemplateRepository tenantBankTemplateRepository) {
		this.platformBankTemplateRepository = platformBankTemplateRepository;
		this.tenantBankTemplateRepository = tenantBankTemplateRepository;
	}

	@Transactional
	public void copyForCompany(UUID tenantId, UUID companyId, String payrollCountry) {
		if (tenantId == null || companyId == null || payrollCountry == null) {
			return;
		}
		String cc = payrollCountry.trim().toUpperCase(Locale.ROOT);
		List<PlatformBankTemplateEntity> sources = platformBankTemplateRepository
				.findByCountryCodeAndActiveIsTrueOrderByNameAsc(cc);
		if (sources.isEmpty()) {
			return;
		}
		Instant now = Instant.now();
		for (PlatformBankTemplateEntity p : sources) {
			if (tenantBankTemplateRepository.existsByTenantIdAndCompanyIdAndPlatformBankTemplateId(tenantId, companyId,
					p.getId())) {
				continue;
			}
			TenantBankTemplateEntity t = new TenantBankTemplateEntity();
			t.setId(UUID.randomUUID());
			t.setTenantId(tenantId);
			t.setCompanyId(companyId);
			t.setPlatformBankTemplateId(p.getId());
			t.setCountryCode(p.getCountryCode());
			t.setName(p.getName());
			t.setBankName(p.getBankName());
			t.setSwiftBic(p.getSwiftBic());
			t.setBankCode(p.getBankCode());
			t.setAccountNumberFormat(p.getAccountNumberFormat());
			t.setActive(p.isActive());
			t.setCreatedAt(now);
			t.setUpdatedAt(now);
			tenantBankTemplateRepository.save(t);
		}
	}
}
