package com.wagepayroll.domain.componentgroup;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformComponentHeaderTemplateLocaleRepository
		extends JpaRepository<PlatformComponentHeaderTemplateLocaleEntity, UUID> {

	List<PlatformComponentHeaderTemplateLocaleEntity> findByPlatformComponentHeaderTemplateIdIn(Collection<UUID> headerIds);

	void deleteByPlatformComponentHeaderTemplateId(UUID platformComponentHeaderTemplateId);
}
