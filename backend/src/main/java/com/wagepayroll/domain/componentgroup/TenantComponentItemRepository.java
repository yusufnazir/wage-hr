package com.wagepayroll.domain.componentgroup;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantComponentItemRepository extends JpaRepository<TenantComponentItemEntity, UUID> {

	Page<TenantComponentItemEntity> findByHeader_IdOrderBySortOrderAscIdAsc(UUID headerId, Pageable pageable);

	Optional<TenantComponentItemEntity> findByIdAndHeader_IdAndHeader_Group_Id(UUID itemId, UUID headerId, UUID groupId);

	boolean existsByHeader_IdAndWageComponent_IdAndIdNot(UUID headerId, UUID wageComponentId, UUID excludeItemId);

	boolean existsByHeader_IdAndWageComponent_Id(UUID headerId, UUID wageComponentId);
}
