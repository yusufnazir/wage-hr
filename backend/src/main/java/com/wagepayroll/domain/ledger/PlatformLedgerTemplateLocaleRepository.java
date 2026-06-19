package com.wagepayroll.domain.ledger;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformLedgerTemplateLocaleRepository extends JpaRepository<PlatformLedgerTemplateLocaleEntity, UUID> {

	List<PlatformLedgerTemplateLocaleEntity> findByPlatformLedgerTemplateIdIn(Collection<UUID> templateIds);

	void deleteByPlatformLedgerTemplateId(UUID platformLedgerTemplateId);
}
