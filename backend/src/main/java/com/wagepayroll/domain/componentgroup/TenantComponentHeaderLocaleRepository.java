package com.wagepayroll.domain.componentgroup;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantComponentHeaderLocaleRepository extends JpaRepository<TenantComponentHeaderLocaleEntity, UUID> {

	List<TenantComponentHeaderLocaleEntity> findByTenantComponentHeaderIdIn(Collection<UUID> headerIds);

	void deleteByTenantComponentHeaderId(UUID tenantComponentHeaderId);
}
