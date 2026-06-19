package com.wagepayroll.domain.componentgroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformComponentHeaderTemplateRepository extends JpaRepository<PlatformComponentHeaderTemplateEntity, UUID> {

	List<PlatformComponentHeaderTemplateEntity> findByGroup_IdOrderBySortOrderAscIdAsc(UUID groupId);

	Page<PlatformComponentHeaderTemplateEntity> findByGroup_IdOrderBySortOrderAscIdAsc(UUID groupId, Pageable pageable);

	Optional<PlatformComponentHeaderTemplateEntity> findByIdAndGroup_Id(UUID headerId, UUID groupId);
}
