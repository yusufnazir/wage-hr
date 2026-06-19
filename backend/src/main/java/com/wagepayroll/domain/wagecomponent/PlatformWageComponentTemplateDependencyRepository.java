package com.wagepayroll.domain.wagecomponent;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformWageComponentTemplateDependencyRepository
		extends JpaRepository<PlatformWageComponentTemplateDependencyEntity, UUID> {

	List<PlatformWageComponentTemplateDependencyEntity> findByPlatformWageComponentTemplateId(UUID templateId);

	@Query("""
			SELECT d FROM PlatformWageComponentTemplateDependencyEntity d
			WHERE d.platformWageComponentTemplateId IN :templateIds
			   OR d.dependsOnTemplateId IN :templateIds
			""")
	List<PlatformWageComponentTemplateDependencyEntity> findTouchingTemplates(
			@Param("templateIds") Collection<UUID> templateIds);

	void deleteByPlatformWageComponentTemplateId(UUID templateId);
}
