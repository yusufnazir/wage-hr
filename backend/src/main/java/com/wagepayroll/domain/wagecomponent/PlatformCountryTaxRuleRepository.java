package com.wagepayroll.domain.wagecomponent;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformCountryTaxRuleRepository
		extends JpaRepository<PlatformCountryTaxRuleEntity, UUID>, JpaSpecificationExecutor<PlatformCountryTaxRuleEntity> {

	List<PlatformCountryTaxRuleEntity> findByCountryCodeAndActiveIsTrue(String countryCode);

	boolean existsByCountryCodeAndRuleCodeAndEffectiveFrom(String countryCode, String ruleCode, LocalDate effectiveFrom);
}
