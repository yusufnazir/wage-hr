package com.wagepayroll.domain.country;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformCountryTranslationRepository extends JpaRepository<PlatformCountryTranslationEntity, UUID> {

	List<PlatformCountryTranslationEntity> findByCountryIdIn(Collection<UUID> countryIds);

	Optional<PlatformCountryTranslationEntity> findByCountryIdAndLocale(UUID countryId, String locale);

	void deleteByCountryId(UUID countryId);
}
