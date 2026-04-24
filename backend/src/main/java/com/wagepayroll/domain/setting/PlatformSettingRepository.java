package com.wagepayroll.domain.setting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingRepository extends JpaRepository<PlatformSettingEntity, UUID> {

	List<PlatformSettingEntity> findAllByOrderByKeyAsc();

	Optional<PlatformSettingEntity> findByKey(String key);
}
