package com.wagepayroll.domain.plan;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommercialPlanFeatureRepository extends JpaRepository<CommercialPlanFeatureEntity, UUID> {

	long countByCommercialPlanId(UUID commercialPlanId);

	List<CommercialPlanFeatureEntity> findByCommercialPlanIdOrderByPlanFeatureId(UUID commercialPlanId);

	@Modifying
	@Query("delete from CommercialPlanFeatureEntity c where c.commercialPlanId = :planId")
	void deleteByCommercialPlanId(@Param("planId") UUID planId);
}
