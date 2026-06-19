package com.wagepayroll.domain.componentgroup;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformComponentGroupTemplateLocaleRepository
		extends JpaRepository<PlatformComponentGroupTemplateLocaleEntity, UUID> {

	List<PlatformComponentGroupTemplateLocaleEntity> findByPlatformComponentGroupTemplateIdIn(Collection<UUID> groupIds);

	void deleteByPlatformComponentGroupTemplateId(UUID platformComponentGroupTemplateId);
}
