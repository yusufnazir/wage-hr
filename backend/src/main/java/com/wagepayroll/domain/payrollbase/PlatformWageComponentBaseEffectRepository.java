package com.wagepayroll.domain.payrollbase;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformWageComponentBaseEffectRepository
		extends JpaRepository<PlatformWageComponentBaseEffectEntity, UUID> {

	List<PlatformWageComponentBaseEffectEntity> findByPlatformWageComponentIdAndActiveIsTrue(UUID platformWageComponentId);

	boolean existsByPlatformWageComponentIdAndPlatformPayrollBaseId(UUID componentId, UUID payrollBaseId);
}
