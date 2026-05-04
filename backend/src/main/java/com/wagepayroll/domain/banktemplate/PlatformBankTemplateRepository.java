package com.wagepayroll.domain.banktemplate;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformBankTemplateRepository
		extends JpaRepository<PlatformBankTemplateEntity, UUID>, JpaSpecificationExecutor<PlatformBankTemplateEntity> {

	List<PlatformBankTemplateEntity> findByCountryCodeAndActiveIsTrueOrderByNameAsc(String countryCode);
}
