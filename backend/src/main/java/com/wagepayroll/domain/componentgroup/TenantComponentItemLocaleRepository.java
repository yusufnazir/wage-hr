package com.wagepayroll.domain.componentgroup;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantComponentItemLocaleRepository extends JpaRepository<TenantComponentItemLocaleEntity, UUID> {

	List<TenantComponentItemLocaleEntity> findByTenantComponentItemIdIn(Collection<UUID> itemIds);

	void deleteByTenantComponentItemId(UUID tenantComponentItemId);
}
