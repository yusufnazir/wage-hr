package com.wagepayroll.domain.ledger;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformLedgerTemplateRepository
		extends JpaRepository<PlatformLedgerTemplateEntity, UUID>, JpaSpecificationExecutor<PlatformLedgerTemplateEntity> {

	List<PlatformLedgerTemplateEntity> findByCountryCodeAndActiveIsTrueOrderByCodeAsc(String countryCode);

	boolean existsByCountryCodeAndCodeIgnoreCase(String countryCode, String code);

	boolean existsByCountryCodeAndCodeIgnoreCaseAndIdNot(String countryCode, String code, UUID notId);
}
