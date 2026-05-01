package com.wagepayroll.domain.currency;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantExchangeRateRepository extends JpaRepository<TenantExchangeRateEntity, UUID> {

	Page<TenantExchangeRateEntity> findByTenantId(UUID tenantId, Pageable pageable);

	Optional<TenantExchangeRateEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByTenantIdAndFromCurrencyIdAndToCurrencyIdAndEffectiveDate(
			UUID tenantId,
			UUID fromCurrencyId,
			UUID toCurrencyId,
			LocalDate effectiveDate);

	boolean existsByTenantIdAndFromCurrencyIdAndToCurrencyIdAndEffectiveDateAndIdNot(
			UUID tenantId,
			UUID fromCurrencyId,
			UUID toCurrencyId,
			LocalDate effectiveDate,
			UUID id);

	Optional<TenantExchangeRateEntity> findFirstByTenantIdAndFromCurrencyIdAndToCurrencyIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
			UUID tenantId,
			UUID fromCurrencyId,
			UUID toCurrencyId,
			LocalDate effectiveDate);
}
