package com.wagepayroll.domain.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPlanRepository extends JpaRepository<CommercialPlanEntity, UUID> {

	List<CommercialPlanEntity> findAllByOrderBySortOrderAscCodeAsc();

	Optional<CommercialPlanEntity> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	Optional<CommercialPlanEntity> findByStripeSubscriptionPriceId(String stripeSubscriptionPriceId);

	boolean existsByStripeSubscriptionPriceIdAndIdNot(String stripeSubscriptionPriceId, UUID id);

	Optional<CommercialPlanEntity> findByPaypalBillingPlanId(String paypalBillingPlanId);

	boolean existsByPaypalBillingPlanIdAndIdNot(String paypalBillingPlanId, UUID id);
}
