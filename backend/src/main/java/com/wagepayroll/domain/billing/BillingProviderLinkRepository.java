package com.wagepayroll.domain.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingProviderLinkRepository extends JpaRepository<BillingProviderLinkEntity, UUID> {

	List<BillingProviderLinkEntity> findAllByTenantIdOrderByProviderAsc(UUID tenantId);

	Optional<BillingProviderLinkEntity> findByTenantIdAndProvider(UUID tenantId, String provider);

	Optional<BillingProviderLinkEntity> findByProviderAndExternalCustomerId(String provider, String externalCustomerId);
}
