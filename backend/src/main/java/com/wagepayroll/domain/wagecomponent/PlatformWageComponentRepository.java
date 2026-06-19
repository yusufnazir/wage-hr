package com.wagepayroll.domain.wagecomponent;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformWageComponentRepository extends JpaRepository<PlatformWageComponentEntity, UUID> {

	Page<PlatformWageComponentEntity> findByCountryCodeOrderByCodeAsc(String countryCode, Pageable pageable);

	List<PlatformWageComponentEntity> findByCountryCodeAndActiveIsTrueOrderByProcessingOrderAsc(String countryCode);

	boolean existsByCountryCodeAndCodeIgnoreCaseAndStatutoryIsTrueAndActiveIsTrue(String countryCode, String code);
}
