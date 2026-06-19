package com.wagepayroll.domain.componentgroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformComponentItemTemplateRepository extends JpaRepository<PlatformComponentItemTemplateEntity, UUID> {

	List<PlatformComponentItemTemplateEntity> findByHeader_IdOrderBySortOrderAscIdAsc(UUID headerId);

	Page<PlatformComponentItemTemplateEntity> findByHeader_IdOrderBySortOrderAscIdAsc(UUID headerId, Pageable pageable);

	Optional<PlatformComponentItemTemplateEntity> findByIdAndHeader_IdAndHeader_Group_Id(UUID itemId, UUID headerId,
			UUID groupId);

	boolean existsByHeader_IdAndWageComponentTemplate_IdAndIdNot(UUID headerId, UUID wageComponentTemplateId,
			UUID excludeItemId);

	boolean existsByHeader_IdAndWageComponentTemplate_Id(UUID headerId, UUID wageComponentTemplateId);
}
