package com.wagepayroll.domain.billing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingUsageEventRepository extends JpaRepository<BillingUsageEventEntity, UUID> {

	long countByTenantId(UUID tenantId);

	@Query("select e.metricKey, coalesce(sum(e.quantity), 0) from BillingUsageEventEntity e "
			+ "where e.tenantId = :tenantId and e.recordedAt >= :start and e.recordedAt < :end group by e.metricKey")
	List<Object[]> sumQuantityByMetricForTenantRecordedBetween(@Param("tenantId") UUID tenantId, @Param("start") Instant start,
			@Param("end") Instant end);

	@Query("select distinct e.tenantId from BillingUsageEventEntity e where e.recordedAt >= :start and e.recordedAt < :end")
	List<UUID> findDistinctTenantIdByRecordedAtBetween(@Param("start") Instant start, @Param("end") Instant end);
}
