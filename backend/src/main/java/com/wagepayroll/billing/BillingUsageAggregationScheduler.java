package com.wagepayroll.billing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.wagepayroll.domain.billing.BillingUsageEventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly (UTC) recompute of {@code billing_usage_aggregate} for the <strong>previous</strong> UTC calendar day, for
 * each tenant that had at least one {@code billing_usage_event} in that day. Same deterministic rules as
 * {@link BillingUsageAggregationService#recomputeDailyAggregatesForTenant}.
 */
@Component
@ConditionalOnProperty(prefix = "app.billing.usage-aggregation", name = "scheduled-enabled", havingValue = "true",
		matchIfMissing = true)
public class BillingUsageAggregationScheduler {

	private static final Logger log = LoggerFactory.getLogger(BillingUsageAggregationScheduler.class);

	private final BillingUsageEventRepository billingUsageEventRepository;
	private final BillingUsageAggregationService billingUsageAggregationService;

	public BillingUsageAggregationScheduler(BillingUsageEventRepository billingUsageEventRepository,
			BillingUsageAggregationService billingUsageAggregationService) {
		this.billingUsageEventRepository = billingUsageEventRepository;
		this.billingUsageAggregationService = billingUsageAggregationService;
	}

	@Scheduled(cron = "${app.billing.usage-aggregation.daily-cron:0 15 2 * * *}", zone = "UTC")
	public void recomputePreviousUtcDayForTenantsWithEvents() {
		LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
		Instant windowStart = yesterday.atStartOfDay(BillingUsageAggregationService.AGGREGATION_ZONE).toInstant();
		Instant windowEnd = yesterday.plusDays(1).atStartOfDay(BillingUsageAggregationService.AGGREGATION_ZONE).toInstant();
		List<UUID> tenantIds = billingUsageEventRepository.findDistinctTenantIdByRecordedAtBetween(windowStart, windowEnd);
		if (tenantIds.isEmpty()) {
			return;
		}
		log.info("billing_usage_aggregate scheduled recompute for {} UTC day, {} tenant(s)", yesterday, tenantIds.size());
		for (UUID tenantId : tenantIds) {
			try {
				billingUsageAggregationService.recomputeDailyAggregatesForTenant(tenantId, yesterday, yesterday);
			}
			catch (RuntimeException ex) {
				log.warn("billing_usage_aggregate recompute failed for tenant {} day {}", tenantId, yesterday, ex);
			}
		}
	}
}
