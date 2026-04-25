package com.wagepayroll.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.domain.billing.BillingUsageAggregateEntity;
import com.wagepayroll.domain.billing.BillingUsageAggregateRepository;
import com.wagepayroll.domain.billing.BillingUsageEventRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deterministic daily aggregation: for each UTC calendar day in range, deletes existing
 * {@code billing_usage_aggregate} rows for that tenant + {@code period_start}, then recomputes totals from
 * {@code billing_usage_event} in {@code [period_start, period_end)}. Safe to run repeatedly (idempotent totals).
 */
@Service
public class BillingUsageAggregationService {

	public static final ZoneOffset AGGREGATION_ZONE = ZoneOffset.UTC;

	private static final int DEFAULT_LIST_DAYS = 30;
	private static final int MAX_RECOMPUTE_SPAN_DAYS = 366;

	private final BillingUsageEventRepository billingUsageEventRepository;
	private final BillingUsageAggregateRepository billingUsageAggregateRepository;

	public BillingUsageAggregationService(BillingUsageEventRepository billingUsageEventRepository,
			BillingUsageAggregateRepository billingUsageAggregateRepository) {
		this.billingUsageEventRepository = billingUsageEventRepository;
		this.billingUsageAggregateRepository = billingUsageAggregateRepository;
	}

	@Transactional
	public void recomputeDailyAggregatesForTenant(UUID tenantId, LocalDate fromInclusive, LocalDate toInclusive) {
		if (tenantId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TENANT_ID_REQUIRED");
		}
		if (fromInclusive == null || toInclusive == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_AGGREGATE_DATE_RANGE_REQUIRED");
		}
		if (toInclusive.isBefore(fromInclusive)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_AGGREGATE_DATE_RANGE_INVALID");
		}
		long spanDays = fromInclusive.until(toInclusive, java.time.temporal.ChronoUnit.DAYS) + 1;
		if (spanDays > MAX_RECOMPUTE_SPAN_DAYS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_AGGREGATE_DATE_RANGE_TOO_LARGE");
		}
		for (LocalDate d = fromInclusive; !d.isAfter(toInclusive); d = d.plusDays(1)) {
			Instant periodStart = d.atStartOfDay(AGGREGATION_ZONE).toInstant();
			Instant periodEnd = d.plusDays(1).atStartOfDay(AGGREGATION_ZONE).toInstant();
			billingUsageAggregateRepository.deleteByTenantIdAndPeriodStart(tenantId, periodStart);
			List<Object[]> sums = billingUsageEventRepository.sumQuantityByMetricForTenantRecordedBetween(tenantId, periodStart,
					periodEnd);
			Instant lastAggregatedAt = Instant.now();
			for (Object[] row : sums) {
				String metricKey = (String) row[0];
				BigDecimal total = (BigDecimal) row[1];
				if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
					continue;
				}
				BillingUsageAggregateEntity agg = new BillingUsageAggregateEntity();
				agg.setId(UUID.randomUUID());
				agg.setTenantId(tenantId);
				agg.setMetricKey(metricKey);
				agg.setPeriodStart(periodStart);
				agg.setPeriodEnd(periodEnd);
				agg.setTotalQuantity(total);
				agg.setLastAggregatedAt(lastAggregatedAt);
				agg.setExternalSynced(false);
				agg.setExternalSyncedAt(null);
				billingUsageAggregateRepository.save(agg);
			}
		}
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> listAggregatesForTenant(UUID tenantId, BillingMetricKey metricKeyOrNull, LocalDate periodStartOrNull,
			LocalDate periodEndOrNull) {
		if (tenantId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TENANT_ID_REQUIRED");
		}
		LocalDate to = periodEndOrNull != null ? periodEndOrNull : LocalDate.now(AGGREGATION_ZONE);
		LocalDate from = periodStartOrNull != null ? periodStartOrNull : to.minusDays(DEFAULT_LIST_DAYS);
		if (to.isBefore(from)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_AGGREGATE_DATE_RANGE_INVALID");
		}
		Instant fromInstant = from.atStartOfDay(AGGREGATION_ZONE).toInstant();
		Instant toInstant = to.atStartOfDay(AGGREGATION_ZONE).toInstant();
		List<BillingUsageAggregateEntity> rows;
		if (metricKeyOrNull != null) {
			rows = billingUsageAggregateRepository.findByTenantIdAndMetricKeyAndPeriodStartBetweenOrderByPeriodStartAsc(tenantId,
					metricKeyOrNull.wireValue(), fromInstant, toInstant);
		}
		else {
			rows = billingUsageAggregateRepository.findByTenantIdAndPeriodStartBetweenOrderByPeriodStartAscMetricKeyAsc(tenantId,
					fromInstant, toInstant);
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for (BillingUsageAggregateEntity e : rows) {
			out.add(toWireMap(e));
		}
		return out;
	}

	private static Map<String, Object> toWireMap(BillingUsageAggregateEntity e) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("metricKey", e.getMetricKey());
		m.put("periodStart", e.getPeriodStart().toString());
		m.put("periodEnd", e.getPeriodEnd().toString());
		m.put("totalQuantity", e.getTotalQuantity());
		m.put("lastAggregatedAt", e.getLastAggregatedAt().toString());
		m.put("externalSynced", e.isExternalSynced());
		m.put("externalSyncedAt", e.getExternalSyncedAt() != null ? e.getExternalSyncedAt().toString() : null);
		return m;
	}
}
