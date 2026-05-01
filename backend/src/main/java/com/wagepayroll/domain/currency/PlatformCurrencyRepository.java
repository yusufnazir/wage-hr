package com.wagepayroll.domain.currency;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformCurrencyRepository extends JpaRepository<PlatformCurrencyEntity, UUID> {

	List<PlatformCurrencyEntity> findAllByOrderBySortOrderAscCodeAsc();

	List<PlatformCurrencyEntity> findByActiveTrueOrderBySortOrderAscCodeAsc();

	List<PlatformCurrencyEntity> findByCodeIn(Collection<String> codes);

	@Query("select c from PlatformCurrencyEntity c where lower(c.code) = lower(:code)")
	Optional<PlatformCurrencyEntity> findByCodeIgnoreCase(@Param("code") String code);

	@Query("select count(c) > 0 from PlatformCurrencyEntity c where lower(c.code) = lower(:code)")
	boolean existsByCodeIgnoreCase(@Param("code") String code);
}
