package com.wagepayroll.domain.subscription;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscriptionEntity, UUID> {

	Optional<TenantSubscriptionEntity> findByTenantId(UUID tenantId);

	boolean existsByCommercialPlanId(UUID commercialPlanId);
}
