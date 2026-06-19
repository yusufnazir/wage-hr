package com.wagepayroll.domain.componentgroup;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantComponentGroupLocaleRepository extends JpaRepository<TenantComponentGroupLocaleEntity, UUID> {

	List<TenantComponentGroupLocaleEntity> findByTenantComponentGroupIdIn(Collection<UUID> groupIds);

	void deleteByTenantComponentGroupId(UUID tenantComponentGroupId);
}
