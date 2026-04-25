package com.wagepayroll.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.wagepayroll.billing.BillingUsageAggregationService;
import com.wagepayroll.domain.billing.BillingUsageAggregateRepository;
import com.wagepayroll.domain.billing.BillingUsageEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantBillingUsageAggregationIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final UUID DEMO_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private BillingUsageAggregationService billingUsageAggregationService;

	@Autowired
	private BillingUsageAggregateRepository billingUsageAggregateRepository;

	@Autowired
	private BillingUsageEventRepository billingUsageEventRepository;

	@Test
	void distinctTenantQueryFindsTenantsWithEventsInWindow() throws Exception {
		LocalDate day = LocalDate.now(ZoneOffset.UTC);
		Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant end = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		postUsage("{\"metricKey\":\"PAYROLL_RUN\",\"quantity\":1,\"idempotencyKey\":\"agg-distinct-1\"}");
		List<UUID> tenants = billingUsageEventRepository.findDistinctTenantIdByRecordedAtBetween(start, end);
		assertTrue(tenants.contains(DEMO_TENANT_ID));
	}

	@Test
	void multipleEventsRollUpToCorrectDailyAggregate() throws Exception {
		LocalDate day = LocalDate.now(ZoneOffset.UTC);
		postUsage("{\"metricKey\":\"PAYROLL_RUN\",\"quantity\":1,\"idempotencyKey\":\"agg-m1\"}");
		postUsage("{\"metricKey\":\"PAYROLL_RUN\",\"quantity\":2,\"idempotencyKey\":\"agg-m2\"}");
		postUsage("{\"metricKey\":\"PAYROLL_RUN\",\"quantity\":3,\"idempotencyKey\":\"agg-m3\"}");

		billingUsageAggregationService.recomputeDailyAggregatesForTenant(DEMO_TENANT_ID, day, day);

		assertEquals(1, billingUsageAggregateRepository.findByTenantIdAndPeriodStartBetweenOrderByPeriodStartAscMetricKeyAsc(DEMO_TENANT_ID,
				day.atStartOfDay(ZoneOffset.UTC).toInstant(), day.atStartOfDay(ZoneOffset.UTC).toInstant()).size());

		mockMvc.perform(get("/api/v1/tenant/billing/usage-aggregates").param("periodStart", day.toString()).param("periodEnd", day.toString())
				.header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.aggregates.length()").value(1)).andExpect(jsonPath("$.data.aggregates[0].metricKey").value("PAYROLL_RUN"))
				.andExpect(jsonPath("$.data.aggregates[0].totalQuantity").value(6));
	}

	@Test
	void duplicateIdempotencyKeyDoesNotDoubleCountInAggregate() throws Exception {
		LocalDate day = LocalDate.now(ZoneOffset.UTC);
		String body = "{\"metricKey\":\"PAYROLL_RUN\",\"quantity\":5,\"idempotencyKey\":\"agg-idem-dup\"}";
		postUsage(body);
		postUsage(body);

		billingUsageAggregationService.recomputeDailyAggregatesForTenant(DEMO_TENANT_ID, day, day);

		mockMvc.perform(get("/api/v1/tenant/billing/usage-aggregates").param("periodStart", day.toString()).param("periodEnd", day.toString())
				.header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.aggregates.length()").value(1)).andExpect(jsonPath("$.data.aggregates[0].totalQuantity").value(5));
	}

	@Test
	void rerunAggregationIsIdempotent() throws Exception {
		LocalDate day = LocalDate.now(ZoneOffset.UTC);
		postUsage("{\"metricKey\":\"PAYROLL_RUN\",\"quantity\":1,\"idempotencyKey\":\"agg-idem-1\"}");
		postUsage("{\"metricKey\":\"PAYROLL_RUN\",\"quantity\":1,\"idempotencyKey\":\"agg-idem-2\"}");

		billingUsageAggregationService.recomputeDailyAggregatesForTenant(DEMO_TENANT_ID, day, day);
		billingUsageAggregationService.recomputeDailyAggregatesForTenant(DEMO_TENANT_ID, day, day);

		mockMvc.perform(get("/api/v1/tenant/billing/usage-aggregates").param("periodStart", day.toString()).param("periodEnd", day.toString())
				.header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.aggregates.length()").value(1)).andExpect(jsonPath("$.data.aggregates[0].totalQuantity").value(2));

		var rows = billingUsageAggregateRepository.findByTenantIdAndPeriodStartBetweenOrderByPeriodStartAscMetricKeyAsc(DEMO_TENANT_ID,
				day.atStartOfDay(ZoneOffset.UTC).toInstant(), day.atStartOfDay(ZoneOffset.UTC).toInstant());
		assertEquals(1, rows.size());
		assertEquals(0, new BigDecimal("2").compareTo(rows.get(0).getTotalQuantity()));
	}

	@Test
	void multipleMetricsAreSeparated() throws Exception {
		LocalDate day = LocalDate.now(ZoneOffset.UTC);
		postUsage("{\"metricKey\":\"PAYROLL_RUN\",\"quantity\":1,\"idempotencyKey\":\"agg-mm-pr\"}");
		postUsage("{\"metricKey\":\"DOCUMENT_STORAGE_GB\",\"quantity\":\"0.25\",\"idempotencyKey\":\"agg-mm-st\"}");

		billingUsageAggregationService.recomputeDailyAggregatesForTenant(DEMO_TENANT_ID, day, day);

		mockMvc.perform(get("/api/v1/tenant/billing/usage-aggregates").param("periodStart", day.toString()).param("periodEnd", day.toString())
				.header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.aggregates.length()").value(2));

		mockMvc.perform(get("/api/v1/tenant/billing/usage-aggregates").param("metricKey", "PAYROLL_RUN").param("periodStart", day.toString())
				.param("periodEnd", day.toString()).header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.aggregates.length()").value(1)).andExpect(jsonPath("$.data.aggregates[0].totalQuantity").value(1));
	}

	private void postUsage(String json) throws Exception {
		mockMvc.perform(post("/api/v1/tenant/billing/usage-events").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(json).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk());
	}
}
