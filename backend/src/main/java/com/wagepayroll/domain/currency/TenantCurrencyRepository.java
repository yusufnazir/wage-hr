package com.wagepayroll.domain.currency;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface TenantCurrencyRepository extends JpaRepository<TenantCurrencyEntity, UUID> {

	List<TenantCurrencyEntity> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

	@Modifying
	void deleteByTenantId(UUID tenantId);
}
