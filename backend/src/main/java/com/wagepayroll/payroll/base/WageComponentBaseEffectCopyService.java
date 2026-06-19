package com.wagepayroll.payroll.base;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wagepayroll.domain.payrollbase.PlatformWageComponentTemplateBaseEffectEntity;
import com.wagepayroll.domain.payrollbase.PlatformWageComponentTemplateBaseEffectRepository;
import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectEntity;
import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectRepository;

@Service
public class WageComponentBaseEffectCopyService {

	private final PlatformWageComponentTemplateBaseEffectRepository templateEffectRepository;
	private final TenantWageComponentBaseEffectRepository tenantEffectRepository;

	public WageComponentBaseEffectCopyService(PlatformWageComponentTemplateBaseEffectRepository templateEffectRepository,
			TenantWageComponentBaseEffectRepository tenantEffectRepository) {
		this.templateEffectRepository = templateEffectRepository;
		this.tenantEffectRepository = tenantEffectRepository;
	}

	@Transactional
	public void copyTemplateEffectsToTenantComponent(UUID tenantId, UUID platformTemplateId, UUID tenantWageComponentId) {
		List<PlatformWageComponentTemplateBaseEffectEntity> templateEffects = templateEffectRepository
				.findByPlatformWageComponentTemplateIdAndActiveIsTrue(platformTemplateId);
		if (templateEffects.isEmpty()) {
			return;
		}
		Instant now = Instant.now();
		for (PlatformWageComponentTemplateBaseEffectEntity src : templateEffects) {
			if (tenantEffectRepository.existsByTenantIdAndTenantWageComponentIdAndPlatformPayrollBaseId(tenantId,
					tenantWageComponentId, src.getPlatformPayrollBaseId())) {
				continue;
			}
			TenantWageComponentBaseEffectEntity dst = new TenantWageComponentBaseEffectEntity();
			dst.setId(UUID.randomUUID());
			dst.setTenantId(tenantId);
			dst.setTenantWageComponentId(tenantWageComponentId);
			dst.setPlatformPayrollBaseId(src.getPlatformPayrollBaseId());
			dst.setEffectDirection(src.getEffectDirection());
			dst.setEffectCalculationType(src.getEffectCalculationType());
			dst.setEffectValue(src.getEffectValue());
			dst.setPriority(src.getPriority());
			dst.setEffectiveFrom(src.getEffectiveFrom());
			dst.setEffectiveUntil(src.getEffectiveUntil());
			dst.setActive(src.isActive());
			dst.setCreatedAt(now);
			dst.setUpdatedAt(now);
			tenantEffectRepository.save(dst);
		}
	}
}
