package com.wagepayroll.domain.setting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingRepository extends JpaRepository<TenantSettingEntity, UUID> {

	List<TenantSettingEntity> findByTenantIdOrderByKeyAsc(UUID tenantId);

	Optional<TenantSettingEntity> findByTenantIdAndKey(UUID tenantId, String key);
}
