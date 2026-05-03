package com.wagepayroll.domain.country;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformCountryRepository extends JpaRepository<PlatformCountryEntity, UUID> {

	@Query("""
			select c from PlatformCountryEntity c
			where (:active is null or c.active = :active)
			and (:payrollEnabled is null or c.payrollEnabled = :payrollEnabled)
			and (
				:search = ''
				or lower(c.isoAlpha2) like lower(concat('%', :search, '%'))
				or lower(c.isoAlpha3) like lower(concat('%', :search, '%'))
				or exists (
					select t.id from PlatformCountryTranslationEntity t
					where t.countryId = c.id
					and t.locale in :searchLocales
					and lower(t.name) like lower(concat('%', :search, '%'))
				)
			)
			order by c.isoAlpha2 asc
			""")
	Page<PlatformCountryEntity> search(
			@Param("active") Boolean active,
			@Param("payrollEnabled") Boolean payrollEnabled,
			@Param("search") String search,
			@Param("searchLocales") Set<String> searchLocales,
			Pageable pageable);

	@Query("""
			select count(c) > 0 from PlatformCountryEntity c
			where lower(c.isoAlpha2) = lower(:isoAlpha2)
			and c.active = true
			and c.payrollEnabled = true
			""")
	boolean existsActivePayrollEnabledByIsoAlpha2(@Param("isoAlpha2") String isoAlpha2);

	@Query("select count(c) > 0 from PlatformCountryEntity c where lower(c.isoAlpha2) = lower(:value)")
	boolean existsByIsoAlpha2IgnoreCase(@Param("value") String value);

	@Query("select count(c) > 0 from PlatformCountryEntity c where lower(c.isoAlpha2) = lower(:value) and c.id <> :id")
	boolean existsByIsoAlpha2IgnoreCaseAndIdNot(@Param("value") String value, @Param("id") UUID id);

	@Query("select count(c) > 0 from PlatformCountryEntity c where lower(c.isoAlpha3) = lower(:value)")
	boolean existsByIsoAlpha3IgnoreCase(@Param("value") String value);

	@Query("select count(c) > 0 from PlatformCountryEntity c where lower(c.isoAlpha3) = lower(:value) and c.id <> :id")
	boolean existsByIsoAlpha3IgnoreCaseAndIdNot(@Param("value") String value, @Param("id") UUID id);
}
