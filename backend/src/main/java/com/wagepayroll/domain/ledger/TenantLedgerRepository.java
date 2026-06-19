package com.wagepayroll.domain.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantLedgerRepository extends JpaRepository<TenantLedgerEntity, UUID> {

	List<TenantLedgerEntity> findByTenantIdAndCompanyIdOrderByCodeAsc(UUID tenantId, UUID companyId);

	Optional<TenantLedgerEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	Optional<TenantLedgerEntity> findByTenantIdAndCompanyIdAndPlatformLedgerTemplateId(UUID tenantId, UUID companyId,
			UUID platformLedgerTemplateId);
}
