package com.wagepayroll.domain.payrollbase;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformWageComponentTemplateBaseEffectRepository
		extends JpaRepository<PlatformWageComponentTemplateBaseEffectEntity, UUID> {

	List<PlatformWageComponentTemplateBaseEffectEntity> findByPlatformWageComponentTemplateIdIn(
			Collection<UUID> platformWageComponentTemplateIds);

	List<PlatformWageComponentTemplateBaseEffectEntity> findByPlatformWageComponentTemplateIdAndActiveIsTrue(
			UUID platformWageComponentTemplateId);

	List<PlatformWageComponentTemplateBaseEffectEntity> findByPlatformWageComponentTemplateId(UUID templateId);

	boolean existsByPlatformWageComponentTemplateIdAndPlatformPayrollBaseId(UUID templateId, UUID payrollBaseId);
}
