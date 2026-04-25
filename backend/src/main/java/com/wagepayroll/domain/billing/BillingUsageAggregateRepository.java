package com.wagepayroll.domain.billing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingUsageAggregateRepository extends JpaRepository<BillingUsageAggregateEntity, UUID> {

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from BillingUsageAggregateEntity a where a.tenantId = :tenantId and a.periodStart = :periodStart")
	int deleteByTenantIdAndPeriodStart(@Param("tenantId") UUID tenantId, @Param("periodStart") Instant periodStart);

	List<BillingUsageAggregateEntity> findByTenantIdAndPeriodStartBetweenOrderByPeriodStartAscMetricKeyAsc(UUID tenantId,
			Instant periodStartFromInclusive, Instant periodStartToInclusive);

	List<BillingUsageAggregateEntity> findByTenantIdAndMetricKeyAndPeriodStartBetweenOrderByPeriodStartAsc(UUID tenantId,
			String metricKey, Instant periodStartFromInclusive, Instant periodStartToInclusive);
}
