package com.wagepayroll.domain.componentgroup;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformComponentItemTemplateLocaleRepository
		extends JpaRepository<PlatformComponentItemTemplateLocaleEntity, UUID> {

	List<PlatformComponentItemTemplateLocaleEntity> findByPlatformComponentItemTemplateIdIn(Collection<UUID> itemIds);

	void deleteByPlatformComponentItemTemplateId(UUID platformComponentItemTemplateId);
}
