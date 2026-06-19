package com.wagepayroll.domain.componentgroup;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantComponentHeaderRepository extends JpaRepository<TenantComponentHeaderEntity, UUID> {

	Page<TenantComponentHeaderEntity> findByGroup_IdOrderBySortOrderAscIdAsc(UUID groupId, Pageable pageable);

	Optional<TenantComponentHeaderEntity> findByIdAndGroup_Id(UUID headerId, UUID groupId);
}
