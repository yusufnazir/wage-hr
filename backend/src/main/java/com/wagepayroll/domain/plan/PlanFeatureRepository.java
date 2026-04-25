package com.wagepayroll.domain.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanFeatureRepository extends JpaRepository<PlanFeatureEntity, UUID> {

	List<PlanFeatureEntity> findAllByOrderBySortOrderAscCodeAsc();

	Optional<PlanFeatureEntity> findByCode(String code);
}
