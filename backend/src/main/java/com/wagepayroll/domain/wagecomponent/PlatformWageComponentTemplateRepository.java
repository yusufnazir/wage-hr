package com.wagepayroll.domain.wagecomponent;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformWageComponentTemplateRepository
		extends JpaRepository<PlatformWageComponentTemplateEntity, UUID>,
		JpaSpecificationExecutor<PlatformWageComponentTemplateEntity> {

	List<PlatformWageComponentTemplateEntity> findByCountryCodeAndActiveIsTrueOrderByTemplateCodeAsc(
			String countryCode);

	@Query("select count(t) from PlatformWageComponentTemplateEntity t where "
			+ "t.debitPlatformLedgerTemplateId = :id or t.creditPlatformLedgerTemplateId = :id")
	long countLinkedToLedgerTemplate(@Param("id") UUID id);

	boolean existsByCountryCodeIgnoreCaseAndTemplateCodeIgnoreCase(String countryCode, String templateCode);

	boolean existsByCountryCodeIgnoreCaseAndTemplateCodeIgnoreCaseAndIdNot(String countryCode, String templateCode, UUID id);
}
