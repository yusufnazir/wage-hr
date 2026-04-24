package com.wagepayroll.domain.navmenu;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NavMenuItemRepository extends JpaRepository<NavMenuItemEntity, UUID> {

	List<NavMenuItemEntity> findByTenantIdOrderBySortOrderAsc(UUID tenantId);
}
